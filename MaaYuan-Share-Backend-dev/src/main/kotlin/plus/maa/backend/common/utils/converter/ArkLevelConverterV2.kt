package plus.maa.backend.common.utils.converter

import org.mapstruct.Mapper
import plus.maa.backend.controller.response.copilot.ArkLevelInfoV2
import plus.maa.backend.repository.entity.ArkLevel

@Mapper(componentModel = "spring")
interface ArkLevelConverterV2 {
    fun convert(arkLevel: ArkLevel): ArkLevelInfoV2

    fun convert(arkLevels: List<ArkLevel>): List<ArkLevelInfoV2>
}
