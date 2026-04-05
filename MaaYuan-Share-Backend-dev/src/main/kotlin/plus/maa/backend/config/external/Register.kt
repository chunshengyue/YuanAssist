package plus.maa.backend.config.external

/**
 * 注册流程配置
 * - 当 useRegistrationCode = true 时，注册改为使用 registrationCode 校验
 * - 当 useRegistrationCode = false 时，沿用邮箱验证码校验
 */
data class Register(
    var useRegistrationCode: Boolean = false,
    var registrationCode: String = "",
)

