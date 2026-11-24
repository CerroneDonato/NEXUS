package it.cerrone.dailyhub

/** The address the phone uses to reach the PC. A bare IP gets http and the hub port. */
fun hubAddress(typed: String, port: Int = 8765): String {
    val raw = typed.trim().trimEnd('/')
    if (raw.isEmpty()) return ""
    val withScheme = if ("://" in raw) raw else "http://$raw"
    val scheme = withScheme.substringBefore("://")
    val rest = withScheme.substringAfter("://")
    val host = rest.substringBefore("/")
    val path = rest.substringAfter("/", "")
    val hasPort = if (host.startsWith("[")) host.contains("]:") else host.contains(":")
    val authority = if (hasPort || host.isEmpty()) host else "$host:$port"
    return "$scheme://$authority" + if (path.isEmpty()) "" else "/$path"
}
