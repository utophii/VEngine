package com.utophii.shapes

import java.io.File

// shape sources live in plugins/VEngine/shapes, effect files name them relative to that folder
object ShapeFiles {
    const val DIRECTORY = "shapes"

    // the named file, which must exist and stay inside the shapes folder
    fun resolve(directory: File, name: String): File {
        val root = directory.canonicalFile
        val file = File(root, name).canonicalFile
        if (!file.toPath().startsWith(root.toPath())) {
            throw ShapeException("'$name' points outside the $DIRECTORY folder")
        }
        if (!file.isFile) {
            throw ShapeException("'$name' was not found in plugins/VEngine/$DIRECTORY")
        }
        return file
    }
}
