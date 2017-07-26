/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.replacementFor

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.quickfix.replacement.PatternAnnotationData
import org.jetbrains.kotlin.idea.util.getImplicitReceiversWithInstanceToExpression
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall

data class ReplacementForPatternMatch(
        val callable: FunctionDescriptor,
        val annotationData: PatternAnnotationData,
        val arguments: Map<ParameterDescriptor, KtExpression>
)

//TODO: where to check that pattern has all parameters of the callable included?
class PatternMatcher(
        private val callable: FunctionDescriptor,
        private val pattern: KtExpression,
        analyzePattern: () -> BindingContext?
) {
    private val parametersByName: Map<String, ValueParameterDescriptor> = callable.valueParameters.associateBy { it.name.asString() }
    private val patternBindingContext by lazy { analyzePattern() }

    //TODO: type arguments
    private class Match(val arguments: Map<ParameterDescriptor, KtExpression>) {
        fun isEmpty() = arguments.isEmpty()

        companion object {
            val EMPTY = Match(emptyMap())
        }
    }

    //TODO: handle safe calls

    fun matchExpression(expression: KtExpression, bindingContext: BindingContext, annotationData: PatternAnnotationData): ReplacementForPatternMatch? {
        //TODO: which other "not really expressions" exist?
        if (expression.getQualifiedExpressionForSelector() != null) return null

        val match = doMatchExpression(expression, bindingContext) ?: return null
        return ReplacementForPatternMatch(callable, annotationData, match.arguments)
                .takeIf { it.checkCorrectness(expression, bindingContext) }
    }

    private fun doMatchExpression(expression: KtExpression, bindingContext: BindingContext?): Match? {
        return matchExpressionPart(expression, pattern, bindingContext)
    }

    private fun matchExpressionPart(
            part: PsiElement,
            patternPart: PsiElement,
            bindingContext: BindingContext?
    ): Match? {
        if (part is KtParenthesizedExpression) {
            return matchExpressionPart(part.expression ?: return null, patternPart, bindingContext)
        }

        if (part is KtDotQualifiedExpression && patternPart !is KtDotQualifiedExpression) {
            if (patternPart !is KtExpression) return null
            val selector = part.selectorExpression ?: return null
            val receiver = part.receiverExpression
            val selectorMatch = matchExpressionPart(selector, patternPart, bindingContext) ?: return null

            val expressionForReceiver = expressionForImplicitReceiver(patternPart, patternBindingContext ?: return null) ?: return null
            val receiverMatch = matchExpressionPart(receiver, expressionForReceiver, null) // match it without BindingContext because expressionForReceiver is non-physical
                                ?: return null

            return selectorMatch.add(receiverMatch, bindingContext)
        }

        when (patternPart) {
            is KtSimpleNameExpression -> {
                return matchToSimpleName(part, patternPart, bindingContext)
            }

            is KtParenthesizedExpression -> {
                return matchExpressionPart(part, patternPart.expression ?: return null, bindingContext)
            }

            is KtThisExpression -> {
                if (part !is KtExpression) return null
                if (patternPart.labelQualifier != null) return null //TODO: can we support this at all?
                val receiverParameter = callable.extensionReceiverParameter
                                        ?: callable.dispatchReceiverParameter
                                        ?: return null /*TODO?*/
                return Match(mapOf(receiverParameter to part))
            }

            is KtDotQualifiedExpression -> {
                if (part !is KtDotQualifiedExpression && part is KtElement) {
                    if (bindingContext == null) return null

                    val patternSelector = patternPart.selectorExpression ?: return null
                    val patternReceiver = patternPart.receiverExpression
                    val selectorMatch = matchExpressionPart(part, patternSelector, bindingContext) ?: return null

                    val expressionForReceiver = expressionForImplicitReceiver(part, bindingContext) ?: return null
                    val receiverMatch = matchExpressionPart(expressionForReceiver, patternReceiver, null) // match it without BindingContext because expressionForReceiver is non-physical
                                        ?: return null

                    return selectorMatch.add(receiverMatch, bindingContext)
                }
            }
        }

        return matchToNonSpecificElement(part, patternPart, bindingContext)
    }

    private fun matchToSimpleName(
            part: PsiElement,
            patternPart: KtSimpleNameExpression,
            bindingContext: BindingContext?
    ): Match? {
        if (part !is KtExpression) return null

        val patternName = patternPart.getReferencedName()
        val parameter = parametersByName[patternName] //TODO: what if name is shadowed inside the pattern (can we ignore such exotic case?)
        if (parameter != null) {
            return Match(mapOf(parameter to part))
        }

        if (part !is KtSimpleNameExpression || part.getReferencedName() != patternName) return null

        if (bindingContext != null && patternBindingContext != null) {
            val target = bindingContext[BindingContext.REFERENCE_TARGET, part]
            val patternTarget = patternBindingContext!![BindingContext.REFERENCE_TARGET, patternPart]
            if (target != patternTarget) return null // TODO: will not be equal if substituted!
        }

        return Match.EMPTY
    }

    private fun matchToNonSpecificElement(
            part: PsiElement,
            patternPart: PsiElement,
            bindingContext: BindingContext?
    ): Match? {
        val elementType = part.node.elementType
        val patternElementType = patternPart.node.elementType
        if (elementType != patternElementType) return null

        if (elementType is KtToken && elementType !is KtSingleValueToken/*optimization*/) {
            return if (part.text == patternPart.text) Match.EMPTY else null
        }

        val children = part.childrenToMatch()
        val patternChildren = patternPart.childrenToMatch()
        if (children.size != patternChildren.size) return null

        var match = Match.EMPTY
        for ((child, patternChild) in children.zip(patternChildren)) {
            val childMatch = matchExpressionPart(child, patternChild, bindingContext) ?: return null
            match = match.add(childMatch, bindingContext) ?: return null
        }

        return match
    }                                              

    private fun PsiElement.childrenToMatch(): Collection<PsiElement> {
        return allChildren.filterNot { it is PsiWhiteSpace || it is PsiComment }.toList()
    }

    private fun Match.add(partMatch: Match, bindingContext: BindingContext?): Match? {
        if (partMatch.isEmpty()) return this
        if (this.isEmpty()) return partMatch
        val newArguments = HashMap(arguments) //TODO: too much new maps created
        for ((parameter, value) in partMatch.arguments) {
            val currentValue = arguments[parameter]
            if (currentValue == null) {
                newArguments.put(parameter, value)
            }
            else {
                //TODO: what if expressions have side effects?
                val match = PatternMatcher(callable, currentValue, { bindingContext }).doMatchExpression(value, bindingContext) ?: return null
                if (!match.isEmpty()) return null //TODO: can this happen?
            }
        }
        return Match(newArguments)
    }
}

private fun expressionForImplicitReceiver(callElement: KtElement, bindingContext: BindingContext): KtExpression? {
    if (callElement is KtNameReferenceExpression) {
        val classDescriptor = bindingContext[BindingContext.REFERENCE_TARGET, callElement] as? ClassDescriptor
        if (classDescriptor != null) {
            val fqName = classDescriptor.importableFqName ?: return null
            if (callElement.getReferencedNameAsName() != fqName.shortName()) return null // import alias used - not supported
            return KtPsiFactory(callElement).createExpression(fqName.parent().render())
        }
    }

    val resolvedCall = callElement.getResolvedCall(bindingContext) ?: return null
    val dispatchReceiver = resolvedCall.dispatchReceiver
    val extensionReceiver = resolvedCall.extensionReceiver
    val receiverValue = when {
        dispatchReceiver != null -> {
            if (extensionReceiver != null) return null // TODO: calls with both receivers?
            dispatchReceiver
        }
        extensionReceiver != null -> extensionReceiver
        else -> null
    }

    if (receiverValue != null) {
        val resolutionScope = callElement.getResolutionScope(bindingContext)!!
        val receiverExpressionFactory = resolutionScope.getImplicitReceiversWithInstanceToExpression().entries
                                                .firstOrNull { it.key.value == receiverValue }
                                                ?.value
                                        ?: return null
        return receiverExpressionFactory.createExpression(KtPsiFactory(callElement))
    }
    else {
        val fqName = resolvedCall.resultingDescriptor.importableFqName ?: return null
        //TODO
        //if (callElement.getReferencedNameAsName() != fqName.shortName()) return null // import alias used - not supported
        return KtPsiFactory(callElement).createExpression(fqName.parent().render())
    }
}
