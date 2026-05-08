package dev.fatihdogmus.agenticreview.editor

import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.LightVirtualFile

class ReviewPageVirtualFile : LightVirtualFile("Agentic Review", PlainTextFileType.INSTANCE, "") {
    init {
        isWritable = false
    }
}
