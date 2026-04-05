package plus.maa.backend.controller.request.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.hibernate.validator.constraints.Length

/**
 * @author AnselYuki
 */
data class RegisterDTO(
    @field:NotBlank(message = "邮箱格式错误")
    @field:Email(message = "邮箱格式错误")
    val email: String,
    @field:NotBlank(message = "用户名长度应在4-24位之间")
    @field:Length(min = 4, max = 24, message = "用户名长度应在4-24位之间")
    val userName: String,
    @field:NotBlank(message = "密码长度必须在8-32位之间")
    @field:Length(min = 8, max = 32, message = "密码长度必须在8-32位之间")
    val password: String,
    // 可选：邮箱验证码（兼容旧流程，当以注册码注册时将被忽略）
    val registrationToken: String? = null,
    // 新增：注册码注册（当开启注册码注册时，必须提供并匹配配置）
    val registrationCode: String? = null,
)
