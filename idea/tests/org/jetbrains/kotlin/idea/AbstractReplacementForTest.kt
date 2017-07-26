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

package org.jetbrains.kotlin.idea

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.replacementFor.AllPatternMatcher
import org.jetbrains.kotlin.idea.replacementFor.ReplacementForPatternMatch
import org.jetbrains.kotlin.idea.replacementFor.replaceExpression
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.dumpTextWithErrors
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.test.KotlinTestUtils
import java.io.File

abstract class AbstractReplacementForTest : KotlinLightCodeInsightFixtureTestCase() {
    protected fun doTest(path: String) {
        val file = File(path)
        val psiFile = myFixture.configureByFile(path) as KtFile

        val fileText = FileUtil.loadFile(file, true)
        ConfigLibraryUtil.configureLibrariesByDirective(myModule, PlatformTestUtil.getCommunityPath(), fileText)

        try {
            val resolutionFacade = psiFile.getResolutionFacade()

            val bindingContext = resolutionFacade.analyzeFullyAndGetResult(listOf(psiFile)).bindingContext

            if (bindingContext.diagnostics.any { it.severity == Severity.ERROR }) {
                error("Errors found in source file:\n${psiFile.dumpTextWithErrors()}")
            }

            val patternMatcher = AllPatternMatcher(project, resolutionFacade)

            project.executeWriteCommand("") {
                val replacements = ArrayList<Pair<KtExpression, ReplacementForPatternMatch>>()
                psiFile.forEachDescendantOfType<KtExpression> { expression ->
                    patternMatcher
                            .match(expression, bindingContext)
                            .forEach { replacements.add(expression to it) }
                }

                for ((expression, match) in replacements) {
                    match.replaceExpression(expression)
                }
            }

            KotlinTestUtils.assertEqualsToFile(File(file.path + ".after"), psiFile.dumpTextWithErrors())
        }
        finally {
            ConfigLibraryUtil.unconfigureLibrariesByDirective(myModule, fileText)
        }
    }
}