# BBQ 新增食材维护点

新增一个 BBQ 食材或饮料时，至少同步检查下面这些位置：

1. `app/src/main/assets/`
   放入对应模板素材，例如 `奶豆腐.png`。

2. `app/src/main/java/com/example/yuanassist/model/DailyBbqModels.kt`
   在 `BbqDemandTemplate` 增加枚举项：
   - `templateName`
   - `displayName`
   - `category`
   - `cookDurationMs`

   如果是食材，还要把它加入 `foodTemplates`。
   如果是饮料，还要把它加入 `drinkTemplates`。
   如果要作为默认槽位之一，再额外修改 `defaultFoodSlotTemplateNames`。
   如果是像 `生鱼.png` 这种“需求模板”和“取料模板”分离的情况，还要设置：
   - `sourceTemplateName`
   - `readyImmediatelyAfterPlacement`

3. `app/src/main/res/layout/fragment_daily_bbq.xml`
   在“基础时长配置”区域增加这个食材的输入框。

4. `app/src/main/java/com/example/yuanassist/ui/DailyBbqFragment.kt`
   把新增食材对应的输入框 id 加进 `FOOD_DURATION_FIELD_IDS`。
   `foodDurationOptions` 直接取 `BbqDemandTemplate.foodTemplates`，通常不用额外改。

5. `app/src/main/java/com/example/yuanassist/ui/TestActivity.kt`
   让测试页能选到并参与 BBQ 识别：
   - 把模板文件名加入 `BBQ_DEMAND_TEMPLATES`
   - 把显示名加入 `TEMPLATE_DISPLAY_NAME_MAP`

6. `app/src/main/java/com/example/yuanassist/core/BbqTemplateScanner.kt`
   如果新食材需要特殊识别阈值，再补到 `thresholdFor(templateName)`；
   如果没有特殊要求，保持默认阈值即可。
   如果是依赖已有食材槽位的衍生需求模板，例如 `鱼` 对应 `生鱼`，还要把它补进识别展开逻辑。

当前新增项示例：

- 食材：`梭子蟹.png` `6s`
- 食材：`鱼.png` `4s`
- 食材需求：`生鱼.png` `0s`，取料复用 `鱼.png`
- 食材：`卷饼.png` `4.8s`
- 食材：`羊肉串.png` `4.8s`
- 食材：`葱.png` `2s`
- 食材：`肉排.png` `6s`
- 食材：`奶豆腐.png` `3s`
- 饮料：`兴霸客.png`
- 饮料：`西凉酥山.png`
- 饮料：`巫血.png`
- 饮料：`孔夫子特调.png`
- 饮料：`特调草划.png`
