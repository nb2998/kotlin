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

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.analysis.analyzeAsReplacement
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.scopes.ExplicitImportsScope
import org.jetbrains.kotlin.resolve.scopes.utils.addImportingScope

fun ReplacementForPatternMatch.replaceExpression(expression: KtExpression): KtExpression {
    val (newExpression, addImport, shortenReceiver) = buildReplacement(this, expression)

    val replaced = expression.replaced(newExpression)

    if (addImport) {
        ImportInsertHelper.getInstance(replaced.project).importDescriptor(replaced.containingKtFile, callable)
    }

    if (shortenReceiver) {
        val selector = (replaced as KtDotQualifiedExpression).selectorExpression
        val shortenOptions = ShortenReferences.Options(removeThis = true)
        return ShortenReferences { shortenOptions }.process(replaced, elementFilter = { element ->
            if (element == selector)
                ShortenReferences.FilterResult.SKIP
            else
                ShortenReferences.FilterResult.PROCESS
        }) as KtExpression
    }
    else {
        return replaced
    }
}

fun ReplacementForPatternMatch.checkCorrectness(expressionToReplace: KtExpression, bindingContext: BindingContext): Boolean {
    val (newExpression, addImport, _) = buildReplacement(this, expressionToReplace)

    var resolutionScope = expressionToReplace.getResolutionScope(bindingContext)!!
    if (addImport) {
        resolutionScope = resolutionScope.addImportingScope(ExplicitImportsScope(listOf(callable)))
    }

    val newBindingContext = newExpression.analyzeAsReplacement(expressionToReplace, bindingContext, resolutionScope)
    //TODO: why doesn't it work without "noSuppression()"?
    //TODO: also check that call is resolved to the right callable?
    return newBindingContext.diagnostics.noSuppression().none { it.severity == Severity.ERROR && newExpression.isAncestor(it.psiElement) }
}

private data class Replacement(
        val expression: KtExpression,
        val addImport: Boolean,
        val shortenReceiver: Boolean
)

private fun buildReplacement(match: ReplacementForPatternMatch, expressionToBeReplaced: KtExpression): Replacement {
    val callable = match.callable
    val receiverEntry = match.arguments.entries.firstOrNull { it.key is ReceiverParameterDescriptor }
    val receiverValue = receiverEntry?.value

    var shortenReceiver = false
    val addImport = callable.isExtension
    val expression = KtPsiFactory(expressionToBeReplaced).buildExpression {
        if (receiverValue != null) {
            appendExpression(receiverValue)
            appendFixedText(".")
            if (receiverValue is KtThisExpression) {
                shortenReceiver = true
            }
        }
        else {
            val qualifier = qualifierFqName(callable)
            if (qualifier != null && !qualifier.isRoot) {
                appendFixedText(qualifier.render() + ".")
                shortenReceiver = true
            }
        }

        appendName(callable.name)

        appendFixedText("(")

        for ((index, parameter) in callable.valueParameters.withIndex()) {
            if (index > 0) {
                appendFixedText(",")
            }
            val value = match.arguments[parameter] ?: error("No value for parameter") //TODO
            appendExpression(value)
        }

        appendFixedText(")")
    }


    return Replacement(expression, addImport, shortenReceiver)
}

private fun qualifierFqName(callable: FunctionDescriptor): FqName? {
    val dispatchReceiver = callable.dispatchReceiverParameter
    val extensionReceiver = callable.extensionReceiverParameter

    fun qualifierFromReceiver(receiverParameter: ReceiverParameterDescriptor): FqName? {
        val classDescriptor = receiverParameter.type.constructor.declarationDescriptor as? ClassDescriptor ?: return null
        return if (classDescriptor.kind.isSingleton)
            classDescriptor.importableFqName
        else
            null
    }

    if (dispatchReceiver != null && extensionReceiver != null) {
        return null //TODO: is this correct?
    }
    else if (dispatchReceiver != null) {
        return qualifierFromReceiver(dispatchReceiver)
    }
    else if (extensionReceiver != null) {
        return qualifierFromReceiver(extensionReceiver)
    }
    else {
        return callable.importableFqName?.parent()
    }
}
