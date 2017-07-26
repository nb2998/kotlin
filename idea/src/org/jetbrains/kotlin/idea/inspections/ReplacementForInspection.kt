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

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.replacementFor.AllPatternMatcher
import org.jetbrains.kotlin.idea.replacementFor.ReplacementForPatternMatch
import org.jetbrains.kotlin.idea.replacementFor.replaceExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ReplacementForInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val ktFile = holder.file as? KtFile ?: return PsiElementVisitor.EMPTY_VISITOR
        val matcher = AllPatternMatcher(holder.project, ktFile.getResolutionFacade())

        return object: KtVisitorVoid() {
            override fun visitExpression(expression: KtExpression) {
                val matches = matcher.match(expression, expression.analyze())
                for (match in matches) {
                    holder.registerProblem(
                            expression,
                            "Can be replaced by '${match.callable.name}' call", //TODO: better presentation
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            PerformReplacementFix(match)
                    )
                }
            }
        }
    }

    private class PerformReplacementFix(match: ReplacementForPatternMatch) : LocalQuickFix {
        private val matchId = id(match)
        private val text = "Replace by '${match.callable.name}' call" //TODO

        override fun getName() = text
        override fun getFamilyName() = "Replace by ReplacementFor annotation pattern"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtExpression ?: return
            val matcher = AllPatternMatcher(project, expression.getResolutionFacade())
            val bindingContext = expression.analyze(BodyResolveMode.PARTIAL)
            val matches = matcher.match(expression, bindingContext)
            val sameMatch = matches.firstOrNull { id(it) == matchId } ?: return // something changed
            sameMatch.replaceExpression(expression)
        }

        companion object {
            private fun id(match: ReplacementForPatternMatch) =
                    DescriptorRenderer.FQ_NAMES_IN_TYPES.render(match.callable) + "#" + match.annotationData.pattern
        }
    }
}