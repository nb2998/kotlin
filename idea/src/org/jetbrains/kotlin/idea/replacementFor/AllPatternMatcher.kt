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

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.idea.quickfix.replacement.PatternAnnotationData
import org.jetbrains.kotlin.idea.quickfix.replacement.analyzeAsExpression
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.stubindex.ReplacementForAnnotationIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addIfNotNull

//TODO: code duplication
private fun KtExpression.callNameExpression(): KtNameReferenceExpression? {
    return when(this) {
        is KtNameReferenceExpression -> this
        is KtCallExpression -> calleeExpression as? KtNameReferenceExpression
        is KtQualifiedExpression -> selectorExpression?.callNameExpression()
        else -> null
    }
}

class AllPatternMatcher(
        private val project: Project,
        private val resolutionFacade: ResolutionFacade
) {

    fun match(expression: KtExpression, bindingContext: BindingContext): Collection<ReplacementForPatternMatch> {
        val name = expression.callNameExpression()?.getReferencedName() ?: return emptyList()
        //TODO: check if it works in compiled code
        val functionDeclarations = ReplacementForAnnotationIndex.getInstance().get(name, project, GlobalSearchScope.allScope(project))
        val result = SmartList<ReplacementForPatternMatch>()
        for (declaration in functionDeclarations) {
            if (declaration.isAncestor(expression)) continue // do not replace inside the function which is annotated by this @ReplacementFor
            val descriptor = declaration.resolveToDescriptor() as FunctionDescriptor
            val patterns = extractReplacementForPatterns(descriptor)
            for (pattern in patterns) {
                //TODO: cache matchers and resolvable patterns
                val resolvablePattern = pattern.analyzeAsExpression(descriptor, resolutionFacade) ?: continue
                val matcher = PatternMatcher(descriptor, resolvablePattern.expression, resolvablePattern.analyze)
                result.addIfNotNull(matcher.matchExpression(expression, bindingContext, pattern))
            }
        }
        return result
    }

    private fun extractReplacementForPatterns(descriptor: CallableDescriptor): Collection<PatternAnnotationData> {
        val fqName = FqName("kotlin.ReplacementFor") //TODO
        return descriptor.annotations
                .filter { it.fqName == fqName }
                .mapNotNull { it.extractData() }

    }

    private fun AnnotationDescriptor.extractData(): PatternAnnotationData? {
        val pattern = argumentValue(kotlin.ReplaceWith::expression.name) as? String ?: return null //TODO
        if (pattern.isEmpty()) return null
        val importValues = argumentValue(kotlin.ReplaceWith::imports.name) as? List<*> ?: return null //TODO
        if (importValues.any { it !is StringValue }) return null
        val imports = importValues.map { (it as StringValue).value }
        return PatternAnnotationData(pattern, imports)
    }
}
