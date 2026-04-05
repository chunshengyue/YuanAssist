package plus.maa.backend.config.external

data class Levels(
    // 启用从 GitHub contents 拉取 v2 JSON（否则使用 classpath）
    var enableGithub: Boolean = false,
    // GitHub api token（可选），如未配置将受 GitHub 匿名速率限制
    var token: String = "",
    // 目标 repo 路径，形如 MaaAssistantArknights/MaaAssistantArknights/dev
    var repoAndBranch: String = "",
    // JSON 文件所在目录（相对于分支），例如 resource/levels
    var jsonDir: String = "",
    // JSON 文件名，例如 arknights-levels.v2.json
    var jsonFile: String = "arknights-levels.v2.json",
    // 本地缓存文件路径（写入最新拉取的 JSON）
    var localCache: String = "./data/levels/arknights-levels.v2.json",
)

