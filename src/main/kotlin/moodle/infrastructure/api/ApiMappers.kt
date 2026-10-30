package moodle.infrastructure.api

import moodle.domain.PublicStatus
import moodle.domain.SiteInfo
import moodle.infrastructure.api.dtos.PublicConfigDto
import moodle.infrastructure.api.dtos.SiteInfoDto

internal fun PublicConfigDto.toDomain(): PublicStatus = PublicStatus(
    siteUrl = wwwroot,
    siteName = sitename,
    webServicesEnabled = enablewebservices == 1,
    mobileServiceEnabled = enablemobilewebservice == 1,
    browserLogin = typeoflogin == 2,
    maintenance = maintenanceenabled == 1
)

internal fun SiteInfoDto.toDomain(): SiteInfo = SiteInfo(
    siteUrl = siteurl,
    siteName = sitename,
    userId = userid,
    fullName = fullname,
    functions = functions.map { it.name }.toSet()
)
