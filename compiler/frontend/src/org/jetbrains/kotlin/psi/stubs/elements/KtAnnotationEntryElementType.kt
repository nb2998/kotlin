/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.psi.stubs.elements

import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.plainContent
import org.jetbrains.kotlin.psi.stubs.KotlinAnnotationEntryStub
import org.jetbrains.kotlin.psi.stubs.impl.KotlinAnnotationEntryStubImpl

class KtAnnotationEntryElementType(
        @NonNls debugName: String
) : KtStubElementType<KotlinAnnotationEntryStub, KtAnnotationEntry>(debugName, KtAnnotationEntry::class.java, KotlinAnnotationEntryStub::class.java) {

    override fun createStub(psi: KtAnnotationEntry, parentStub: StubElement<*>): KotlinAnnotationEntryStub {
        val shortName = KtPsiUtil.getShortName(psi)
        val resultName = shortName?.asString() ?: psi.text
        val valueArgumentList = psi.valueArgumentList
        val hasValueArguments = valueArgumentList != null && !valueArgumentList.arguments.isEmpty()
        val patternName = psi.replacementForPatternName()
        return KotlinAnnotationEntryStubImpl(parentStub, resultName, hasValueArguments, patternName)
    }

    override fun serialize(stub: KotlinAnnotationEntryStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.shortName)
        dataStream.writeBoolean(stub.hasValueArguments)
        dataStream.writeName(stub.replacementForPatternName)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>): KotlinAnnotationEntryStub {
        return KotlinAnnotationEntryStubImpl(
                parentStub,
                dataStream.readName()!!.string,
                dataStream.readBoolean(),
                dataStream.readName()?.string
        )
    }

    override fun indexStub(stub: KotlinAnnotationEntryStub, sink: IndexSink) {
        StubIndexService.getInstance().indexAnnotation(stub, sink)
    }

    private fun KtAnnotationEntry.replacementForPatternName(): String? {
        val referencedName = (calleeExpression?.typeReference?.typeElement as? KtUserType)?.referencedName ?: return null
        if (referencedName != "ReplacementFor") return null //TODO: import aliases
        val firstArgument = valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression ?: return null //TODO: named arguments!
        val expression = try {
            //TODO: escape entries
            KtPsiFactory(project).createExpression(firstArgument.plainContent)
        }
        catch (t: Throwable) {
            return null
        }
        //TODO: we should highlight patterns that are currently not supported
        return expression.callNameExpression()?.getReferencedName()
    }

    private fun KtExpression.callNameExpression(): KtNameReferenceExpression? {
        return when(this) {
            is KtNameReferenceExpression -> this
            is KtCallExpression -> calleeExpression as? KtNameReferenceExpression
            is KtQualifiedExpression -> selectorExpression?.callNameExpression()
            else -> null
        }
    }
}
