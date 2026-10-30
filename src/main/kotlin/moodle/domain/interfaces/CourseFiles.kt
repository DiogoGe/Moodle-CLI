package moodle.domain.interfaces

import moodle.domain.AccessToken
import moodle.domain.entities.CourseFile
import moodle.domain.entities.DownloadedFile

interface CourseFiles {
    fun download(token: AccessToken, file: CourseFile, directory: String): DownloadedFile

    fun readText(token: AccessToken, file: CourseFile): String
}
