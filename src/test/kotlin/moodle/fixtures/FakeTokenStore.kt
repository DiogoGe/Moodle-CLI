package moodle.fixtures

import moodle.domain.AccessToken
import moodle.domain.exceptions.MoodleException
import moodle.domain.interfaces.TokenStore

class FakeTokenStore : TokenStore {
    val values = mutableMapOf<String, AccessToken>()
    var writes = 0
    var deletes = 0
    var rejectWrites = false
    var rejectReads = false
    var rejectDeletes = false

    override fun checkAvailable() = Unit

    override fun load(siteUrl: String): AccessToken? {
        if (rejectReads) throw MoodleException.Credentials("error.credentials.read")
        return values[siteUrl]
    }

    override fun save(siteUrl: String, token: AccessToken) {
        if (rejectWrites) throw MoodleException.Credentials("error.credentials.write")
        values[siteUrl] = token
        writes++
    }

    override fun delete(siteUrl: String) {
        if (rejectDeletes) throw MoodleException.Credentials("error.credentials.delete")
        values.remove(siteUrl)
        deletes++
    }
}
