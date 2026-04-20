# YuanAssist 独立自动化测试工具设计

**目标**

为 YuanAssist 在仓库根目录建设一套独立的 Python 自动化测试工具，覆盖真机 UI 自动化、OCR 与模板匹配专项校验、关键业务链路回归、失败证据归档与报告输出，并沉淀一套可复用的 App 测试接入流程。

**范围**

- 在仓库根目录新增独立 Python 测试工程，不侵入 Android 主工程。
- 基于 `pytest` 组织测试执行、分层用例与产物归档。
- 基于 `adb` 与 `uiautomator2` 实现 Android 真机自动化操作。
- 基于 `OpenCV` 与 `Pillow` 实现模板匹配、区域裁剪、阈值判断与视觉结果输出。
- 接入一套可替换的 OCR 能力，并形成结构化识别结果。
- 覆盖 YuanAssist 首批核心场景：
  - App 启动与环境冒烟
  - 首页与日常页可达性
  - 6-24 主流程回归
  - 鸟食主流程回归
  - 拼图相关主流程回归
  - 模板图与 OCR 识别专项校验
- 输出 Allure 报告与失败证据链。
- 沉淀“接到一个 Android App 后如何开展测试”的标准流程。

**不做**

- 不建设 Web 管理后台或测试平台控制台。
- 不做设备农场、多设备并发调度、云端任务编排。
- 不在本阶段接入 Jenkins、GitHub Actions 或远程设备平台。
- 不一次性覆盖全 App 全场景。
- 不修改 Android 业务代码来迎合测试工具。
- 不提供与当前目标无关的兼容性补丁或降级方案。

## 一、项目现状与设计依据

### 1. 现状判断

YuanAssist 当前是单模块 Android App，主代码集中在：

- `app/src/main/java/com/example/yuanassist/core`
- `app/src/main/java/com/example/yuanassist/ui`
- `app/src/main/java/com/example/yuanassist/model`
- `app/src/main/java/com/example/yuanassist/utils`

当前工程内测试基础较弱，仅存在少量示例级 `test` 与 `androidTest` 文件，不足以支撑业务回归与专项测试。

另一方面，项目已有明显适合测试工具复用的资产与特征：

- `core/` 下已有自动化引擎与运行管理逻辑，如 `AutoTaskEngine.kt`、`InventoryStitchEngine.kt`、`YuanAssistService.kt`
- `assets/` 下已有大量模板图资源，可直接沉淀为视觉测试基线
- 页面与业务入口相对明确，适合抽象页面对象与业务流
- 用户明确具备 Android 真机与 `adb` 调试条件，适合直接以真机自动化为主线

### 2. 设计原则

- 独立工程：测试工具与 Android 主工程解耦，避免互相污染
- 先打底再扩展：优先建设共性能力，再补业务场景
- 分层组织：把设备交互、视觉识别、业务流、测试用例分层，避免端到端脚本失控
- 证据优先：每次失败必须留下足够证据，而不是只有一个失败状态
- 首批可用：第一版交付必须能真实执行冒烟、专项和核心回归，不做空壳

## 二、总体方案

### 1. 推荐方案

采用“基础设施层 + 视觉识别层 + 业务流层 + 报告归档层”的分层一体化方案。

这是本项目最合适的方案，因为：

- 仅做 UI 自动化，无法充分覆盖 YuanAssist 的模板识别与 OCR 风险
- 仅做图像专项，无法证明真实业务链路可用
- 分层一体化既能验证识别能力，也能完成真实真机回归，并且后续扩展成本最低

### 2. 总体结构

测试工具新建于仓库根目录，暂定目录名为 `testsuite/`。

分为 5 层：

1. 基础设施层
   负责设备连接、App 生命周期、截图、日志采集、产物归档、等待机制与通用断言。

2. 设备交互层
   封装 `uiautomator2` 常用操作，包括查找、点击、输入、滑动、等待页面稳定、页面层级抓取与必要的坐标点击。

3. 视觉识别层
   封装模板匹配、OCR、区域截图、匹配阈值、识别结果结构化输出与中间图保存。

4. 业务场景层
   面向 YuanAssist 的页面对象与业务流，抽象首页、日常页、任务入口、执行与结果校验等行为。

5. 结果与流程层
   负责 `pytest` 组织、用例 marker 分层、Allure 结果输出、失败产物挂载与执行脚本入口。

## 三、测试全流程

这套工具不只是脚本集合，而是固定对应一条标准测试流程。后续接手新的 Android App，也沿用这条流程。

### 1. 测试接入

接到 App 后先完成基础确认：

- 包名、启动入口、主业务页面
- 是否要求登录
- 是否依赖网络
- 是否依赖截图、无障碍、悬浮窗、存储、通知等权限
- 设备分辨率、系统版本、是否需要固定 DPI
- 业务是否依赖图片模板、OCR 或区域配置

对 YuanAssist，这一步要重点确认：

- App 安装与启动方式
- 无障碍服务与悬浮窗相关前置
- 日常任务依赖的页面入口
- 模板图与配置文件路径

### 2. 风险拆解

将测试重点拆成 5 类风险：

- 页面与入口风险：页面是否可达、关键控件是否可见
- 自动化流程风险：点击、跳转、等待、状态切换是否稳定
- 视觉识别风险：模板匹配是否稳定、OCR 是否准确
- 配置风险：区域配置、阈值、模板版本是否合理
- 异常恢复风险：流程中断、识别失败、页面偏移后是否可快速定位

### 3. 测试设计

首批用例按 3 类分层：

- 冒烟用例
  - 设备连接成功
  - App 可启动
  - 首页可达
  - 日常页可达
  - 关键模块入口可见

- 核心回归用例
  - 6-24 主流程
  - 鸟食主流程
  - 拼图相关主流程

- 视觉专项用例
  - 模板匹配命中验证
  - OCR 结果验证
  - 区域裁剪与阈值验证
  - 失败样本归档

### 4. 工具落地

优先建设公共能力，再写业务脚本：

- 设备会话与环境探测
- 统一截图与日志采集
- 页面操作封装
- 视觉识别封装
- 断言与失败归档
- 报告输出

### 5. 用例实施

首批实施顺序固定为：

1. 环境冒烟
2. 视觉专项最小闭环
3. 一条核心业务回归
4. 再横向扩展到更多核心用例

### 6. 结果校验与缺陷定位

每条失败用例必须自动沉淀以下证据：

- 当前屏幕截图
- 页面层级信息
- 设备基本信息
- `logcat` 或运行日志片段
- OCR 原始输出
- 模板匹配分数
- 识别中间图与裁剪图

### 7. 回归沉淀

随着工具演进，持续沉淀：

- 稳定回归集
- 失败样本库
- 共性断言与等待机制
- 页面对象与业务流模板
- 新 App 接入清单

## 四、工程结构设计

建议目录如下：

```text
testsuite/
  pyproject.toml
  pytest.ini
  requirements.txt
  README.md
  config/
    project.yaml
    devices.example.yaml
    vision.yaml
  core/
    adb_client.py
    device_session.py
    app_driver.py
    artifact_store.py
    logcat_collector.py
    waiters.py
    assertions.py
  vision/
    template_matcher.py
    ocr_engine.py
    image_loader.py
    image_region.py
    match_result.py
  pages/
    base_page.py
    home_page.py
    daily_page.py
    mainline_624_page.py
    bird_food_page.py
    inventory_stitch_page.py
  flows/
    launch_flow.py
    daily_navigation_flow.py
    mainline_624_flow.py
    bird_food_flow.py
    inventory_stitch_flow.py
  tests/
    smoke/
    regression/
    vision/
  assets/
    templates/
    samples/
  scripts/
    check_env.py
    run_smoke.py
    run_regression.py
    run_vision.py
  artifacts/
    allure-results/
    runs/
```

## 五、核心模块设计

### 1. 设备会话管理

职责：

- 校验 `adb` 是否可用
- 获取设备列表
- 绑定目标设备
- 安装、启动、停止 App
- 执行截图、拉取层级、记录基础信息

要求：

- 一条用例一个独立会话上下文
- 失败时可自动补采最后一屏截图
- 所有设备相关异常统一转换为明确错误类型

### 2. 产物归档

职责：

- 为每次测试运行生成唯一运行目录
- 为每条用例生成独立产物目录
- 存放截图、日志、OCR 输出、模板匹配结果、裁剪图、页面层级、JSON 摘要

要求：

- 目录结构固定，方便追溯
- 失败证据路径可在报告中直接引用

### 3. 视觉识别引擎

职责：

- 从项目模板资源或测试样本加载图片
- 执行模板匹配并输出坐标、分数、阈值判定
- 对指定区域执行 OCR
- 保存识别过程中的裁剪图与标注图

要求：

- OCR 实现可替换，不把调用方绑死在单一提供方
- 所有识别结果统一结构化，便于测试断言与报告消费

### 4. 页面对象

首批页面对象：

- `HomePage`
- `DailyPage`
- `Mainline624Page`
- `BirdFoodPage`
- `InventoryStitchPage`

职责：

- 提供稳定的页面判定方法
- 暴露该页面的关键操作
- 屏蔽底层 `uiautomator2` 细节

### 5. 业务流

首批业务流：

- 启动并进入首页
- 进入日常页
- 进入 6-24 配置与执行入口
- 进入鸟食任务入口
- 进入拼图相关入口

职责：

- 串联页面对象
- 在关键节点插入断言
- 在关键识别点接入视觉校验

## 六、首批测试用例设计

### 1. 冒烟集

- `test_device_connected`
- `test_app_launch_success`
- `test_home_page_reachable`
- `test_daily_page_reachable`
- `test_daily_entries_visible`

目标：

- 验证环境与核心页面没有立即性阻塞

### 2. 核心回归集

- `test_mainline_624_happy_path`
- `test_bird_food_happy_path`
- `test_inventory_stitch_happy_path`

目标：

- 验证首批关键业务链路可执行
- 失败时输出页面与识别证据

### 3. 视觉专项集

- `test_template_match_mainline_entry`
- `test_template_match_bird_food_entry`
- `test_ocr_stage_text_in_target_region`
- `test_region_config_matches_expected_bounds`
- `test_template_failure_artifact_output`

目标：

- 把视觉识别能力从业务流脚本中拆出来单测
- 提前暴露模板、阈值与 OCR 问题

## 七、配置与扩展策略

### 1. 配置文件

首批配置文件建议包括：

- `project.yaml`
  - 包名
  - 启动 Activity
  - 默认截图目录
  - App 相关开关

- `devices.example.yaml`
  - 设备序列号
  - 系统版本
  - 分辨率备注

- `vision.yaml`
  - 模板目录
  - 默认匹配阈值
  - OCR 参数
  - 区域配置

### 2. 扩展方式

后续扩展新场景时，只按既定分层补充：

- 新页面：补 `pages/`
- 新业务流：补 `flows/`
- 新视觉规则：补 `vision/` 或配置
- 新用例：补 `tests/`

避免在测试文件里直接堆设备操作与图像算法。

## 八、报告与可观测性

### 1. 执行入口

通过脚本提供统一入口：

- 环境检查
- 跑冒烟
- 跑核心回归
- 跑视觉专项
- 生成 Allure 报告

### 2. 报告内容

Allure 中至少展示：

- 用例基本信息
- 执行步骤
- 关键断言结果
- 失败截图
- 识别结果摘要
- 产物目录路径

### 3. 失败定位标准

出现失败时，报告中要能快速回答：

- 设备连没连上
- 页面是不是没进入
- 是控件找不到还是模板没命中
- OCR 看到了什么
- 阈值是否过高或区域是否配置错误

## 九、实施顺序

后续实施按以下顺序推进：

1. 创建测试工程骨架与依赖配置
2. 建立环境检测、设备会话与产物归档
3. 建立模板匹配与 OCR 基础封装
4. 建立首页与日常页页面对象
5. 落首批冒烟用例
6. 落 6-24、鸟食、拼图首批回归用例
7. 接入 Allure 报告与运行脚本
8. 补样本与失败归档能力

## 十、验收标准

本阶段测试工具达到“接近团队可长期使用”的标准，具体表现为：

- 能在 Android 真机上通过 `adb` 稳定接入
- 能用 `pytest` 跑通冒烟、视觉专项与首批核心回归
- 能输出 Allure 报告
- 失败时能自动留下定位证据
- 目录与模块边界清晰，后续新增场景不需要重构整体框架
- 具备可复用的 App 测试接入流程说明

## 十一、后续执行约束

进入实现阶段后遵循以下约束：

- 以独立 Python 测试工程为唯一实施载体
- 不主动编译 Android 工程
- 不修改 Android 业务代码来适配测试
- 优先用最短路径完成首批可用闭环
- 所有新增功能按测试工程自己的 TDD 节奏推进
