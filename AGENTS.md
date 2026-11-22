# AGENTS.md — 给接手知时的 agent

知时（Zhishi）是刑君越的安卓课表 App，从作者的另一个应用「留白」（自律 / 应用守护）里把课表相关功能单独拿出来做成的独立应用。Kotlin + Jetpack Compose，包名 / applicationId `com.zhishi.timetable`，可以和留白同时安装。名字取自杜甫「好雨知时节」。动手前先读完这份。

## 范围
- 只做课表和学习相关：课表格子与编辑、教务导入、日历 / 文件 / 截图导入、学期与节次设置、**待办**、月历视图、课表小部件、皮肤、设置、关于与更新、教程。
- 空状态插画（待办、课表没课）：`InkArt.kt` 的一笔水墨圆相 + 带锋的勾（待办）/ 三道短横（课表）+ 小朱印，墨色跟皮肤，进场一笔笔写出（build-16，用户嫌原来的粉圆 + 新芽丑）。不要换回卡通风。
- 待办是用户明确要的（2026-11 改版时提出）：标题、备注、截止时间（精确到分钟的时 / 分滚轮，或全天 `allDay`）、快捷时间（`Todos.preset`、「下次上课前」= `Todos.nextClass`）、关联课程（按课名）、完成、提醒（可提前 `before` 分钟，全天从当天 8:00 算；`Todos.alarmAt` 决定闹钟时刻）；分组 已过期 / 今天 / 即将 / 已完成；左右滑删除；存 SharedPreferences `todo` 的 `items`，课表备份 / 导出里带 `todos` 字段（旧备份没有就不动现有待办）。逻辑在 `Todo.kt`（`Todos` 纯函数有单测 `TodoTest`），界面在 `TodoUi.kt`。
- 到点提醒：`TodoAlarm` 用 AlarmManager，有精确闹钟权限（`canScheduleExactAlarms`）才用精确的，否则不精确；通知权限 POST_NOTIFICATIONS 只在用户第一次打开某条待办的提醒时申请。`TodoReminder` 发通知，开机 / 更新后重排。
- 不做任何应用守护 / 拦截 / 专注 / 使用时长统计；不要加无障碍、使用情况访问、悬浮窗、设备管理、查询全部应用、通知监听这类权限。（待办提醒用的 POST_NOTIFICATIONS、SCHEDULE_EXACT_ALARM、RECEIVE_BOOT_COMPLETED 是允许的。）

## 界面结构（2026-11 改版，照用户给的六张皮肤参考图）
- 底部液态玻璃导航四项：课表 / 待办 / 日历 / 我的（`MainActivity.App`、`BottomBar`）。没有侧栏了。「我的」= 原来的设置页（`MeScreen`，打开名 `settings` 或 `me`），小部件页、关于页从「我的」点进去，左上角返回。
- 课表页顶部：大号粗体「第N周」+「周一 11.21」，右上角「＋」（添加课程和所有导入，底部弹出）和日历图标（学期与显示）。下面一周日期条，今天是柔和的圆角胶囊；左边时间列是每节的上 / 下课时间 + 细虚线时间轴；课块是半透明的柔和色块，没有粗边框和阴影。
- 字体：全部系统无衬线（`Serif` 现在就是默认字体，名字保留免得到处改）。
- 液态玻璃（2026-11-22，用户要的 iOS 26 Liquid Glass 风格）：清透、带水光，**不要模糊 / 毛玻璃**。统一用 `Glass.kt` 的 `Modifier.liquidGlass(shape, level, tint, fill, shadow)`：很透的底（浅色白 ~16%、深色 ~10%，按皮肤主色轻染）+ 左上亮右下淡的细亮边 + 顶部柔光 + 底边暗边 + 只画在形状外的轻投影，内容按形状裁剪（调用处不用再 clip）。`level` 0 = 清透（卡片、按钮、课块），`Glass.THICK` = 底部弹出 / 对话框 / 盖在网页上的条（~98.5% 不透明：实测 94% / 97% 时后面列表的深色字仍有看得出的残影）。导航条的底用 `Glass.bar()`（皮肤卡片色，深色 ~90%、浅色 ~80%，没选中的字用 `C.Soft`），输入框底用 `Glass.field()`（~96%）。`GlassSheet` 的遮罩加重（黑 浅色 45% / 深色 58%）。底部弹出一律用 `GlassSheet`，对话框 `containerColor = Transparent, tonalElevation = 0.dp, shape = DialogShape, modifier = dialogGlass()`。字统一带一圈很淡的光晕（`ZhishiTheme` 里的 `LocalTextStyle` shadow，浅色白、深色黑），主色实底按钮上的字去掉光晕。
- 真·液态玻璃（build-14，用户要「水灵、灵动」的 iOS 26 效果）：Compose BOM 2024.09.03（Compose 1.7，需要 `GraphicsLayer`）。`App` 把整页（插画 + 页面）录进 `LocalGlassPage`、只录插画进 `LocalGlassArt`（`Modifier.glassSource`）。`liquidGlass(lens = LensSpec(...))` 在 API 33+ 用 AGSL `RuntimeShader`（`GlassLens.SRC`）把后面的内容重新画一遍：边缘折射、轻微色散、跟着左上光的亮边、内发光，中间平、不模糊。`cover = true` = 整块都画（导航条、选中水滴，折射 `LocalGlassPage`），否则只画边缘一圈（页面里的按钮，折射插画——页面里的东西不能折射整页，会自己画自己）。不同窗口（底部弹出、对话框）里自动不走透镜。着色器编译 / 设参数 / 绘制 / 录制失败一次（`GlassLens.fail`）就记 `ZhishiGlass` 日志、本进程立刻关透镜，并写进 SharedPreferences `glass` 的 `off`（= 当时的 versionCode），以后每次启动都走老画法，装新版本才再试；API < 33 也是老画法。所有按钮 / 小标签用 `Modifier.glassButton`（透镜 + 按下像水滴鼓起变亮的弹簧，无涟漪）；`Segments` 和底部导航的选中项是会滑动、拉伸、回弹的玻璃水滴。卡片不开透镜（滚动性能），底 12% / 8%。
- 小部件两种样式（`WidgetStyle`，SharedPreferences `widget` 的 `style`）：液态玻璃（默认）/ 经典（build-9 那种实心皮肤底 + 淡插画，代码路径是 `CLASSIC`，别删）。「我的 → 桌面小部件」里切换，切了立刻重画。位图按真实 dp × 密度画（`bitmapSize`，API 31+ 用 `OPTION_APPWIDGET_SIZES`），字不加模糊光晕。
- 课表页的「导入课表」菜单：`Nav.sub` 处理完置 `Nav.NONE`（以前停在 8，每次切回课表页都重新弹）。只有课表空、从没有过课（`ImportPrompt`，SharedPreferences `table` 的 `imported`，`Term.setDesired` 有课就记上）时，每次启动最多自动弹一次。
- 整屏流程（教务导入等）调用 `HideBottomBar()`：打开期间底部导航条收起，免得盖住页面底部按钮。新加整屏页面也要这样。
- 每套皮肤有整屏插画背景（浅 / 深各一张，`res/drawable-nodpi/skin_<id>_<light|dark>.webp`，插画在右上、右边和底部，中间留白）和圆形缩略图（`..._thumb.webp`）；`Backdrop` 铺在所有页面下面，卡片用 `C.Glass` 半透明。插画是 AI 生成后压成 WebP 的，要换就整套换，别直接截参考图（参考图里有手机边框和假界面）。
- 应用图标（build-16，用户要求换成书法字）：书法「知时」竖排 + 右下朱红印章「雨」（好雨知时节）。字形来自 Ma Shan Zheng（马善政楷书，Google Fonts，SIL OFL 1.1，THIRD_PARTY_NOTICES 有署名），用 fontTools 把字形轮廓转成 VectorDrawable 路径，不带字体文件。自适应图标：背景 `drawable/ic_launcher_paper.xml`（宣纸色径向渐变）、前景 `ic_launcher_fg.xml`（墨字 + 印章，都在 66dp 安全区内）、单色层 `ic_mono.xml`（同样的字，印章里「雨」镂空）；mipmap PNG 由同一套矢量渲染。应用内（我的、关于、教程第一页）`AppIcon` 叠画背景 + 前景两层（放大 1.5 倍 = 桌面可见的 72dp）。要改图标就整套重新生成，别只改一层。
- 课程等级（重要 / 普通 / 水课）只是显示用的标签：课块、小部件上标出来，`Term.showLevel` / `Term.widgetLevel` 分别开关，不附带任何限制。

## 发布流程
- `main` = 正式版。推到 main 会触发 `.github/workflows/build.yml`：单元测试 → 构建 → 模拟器冒烟测试（`tools/smoke.sh` 截图，打包发到 `ci-shots` 预发布，也作为 artifact `shots` 上传）→ 发布 release `build-N`（资产 `zhishi-1.N.apk`），应用内更新器会推给用户。
- 新功能一律开分支，用 workflow_dispatch 跑 CI，`preview` job 发 prerelease 预览包（tag `preview-<分支名去掉 -redo>-<run_number>`）给用户试。
- **只有用户明确说"上线 / 直接上 / 测试通过"才合入 main。** 不要自行合并。
- 预览包关闭应用内更新：非 main 分支给 gradle 传 `-Ppreview`（env `PV`），`BuildConfig.PREVIEW=true`；`Updater.enabled()` = `!PREVIEW`。构建后 grep `BuildConfig.java` 核对，单测 `TourAndUpdaterTest` 也测。
- 更新器读 `Updater.REPO = "XingJunyue/zhishi"` 的 releases（tag `build-N`、资产 `zhishi-1.N.apk`），直连失败走镜像。仓库是私有的时候拿不到（界面会提示「仓库还没公开」）。
- 只文档改动推 main 时提交信息加 `[skip ci]`，避免多发一个版本。
- 签名：密钥不在仓库里。CI 用 Actions secret `KS_PASS` 解密 `signing/zhishi.p12.enc`（openssl aes-256-cbc pbkdf2 20万次）还原，别名 `zhishi`；本地构建设 env `KS_FILE`/`KS_PASS`。仓库是公开的，绝不能把 p12 或密码提交进来。换密钥会导致老用户无法覆盖安装。

## 每个版本都要有应用内教程
- 首次安装：可跳过的完整引导（`Tour.FIRST`）。
- 每次更新：可跳过的「新功能」指引（`Tour.WHATS_NEW`，新版本在最前面加一条 `Release(id, pages)`，id 发出去后不要改）。按 versionCode 记录已看版本；新装只弹首次引导、把已有条目都记成看过。
- 设置里「新手教程」「这个版本的新功能」可重看。新功能上线必须同时补指引条目。
- 存储：SharedPreferences `tour`（`code`、`seen`、`firstPending`）。
- 冒烟测试不被挡：`--ez seed true` / `--ez notour true` 把教程记成看过；`--es tour first|new` 直接弹出来截图。

## 多语言
- 界面文字用中文原文当键（`tr("…")`），六种语言在 `app/src/main/assets/i18n/{en,fr,ja,ko,de,es}.json`。
- `TourAndUpdaterTest.allStringsTranslated` 会扫源码里所有 `tr("…")` 文案、教程、皮肤名，六种语言缺一条就失败。新文案必须同时补六种翻译。
- 应用名在 `res/values*/strings.xml`：知时 / Zhishi（日语 知時）。

## 皮肤
- `Theme.kt`：`Skins.ALL` 六套（春雨 chunyu 默认、宣纸 xuanzhi、青瓷 qingci、暮山 mushan、夜航 yehang、樱 ying），每套 `light` / `dark` 两份 `Pal`，加插画背景 / 缩略图（`Skin.art` / `Skin.thumb`）。`poem` 是用户定的一句话简介：春雨「清新如春，细雨润心」、宣纸「书卷墨香，文心相伴」、青瓷「古韵流光，淡雅宁静」、暮山「暮色山河，静谧沉稳」、夜航「星海航行，向梦而行」、樱「樱花烂漫，与你相遇」。模式：跟随系统 / 浅色 / 深色。存储在 SharedPreferences `look`（`skin`、`mode`）。
- 界面用 `C.*`（Compose 状态，换皮肤立即重组）；小部件用 `Skins.palFor(context)`（不依赖 Compose）。`Skins.setSkin/setMode` 会立刻重画所有小部件。
- 课程颜色 `COURSE_COLORS`（浅底, 深字），深色模式经 `courseTone` 变成深底浅字，界面和小部件一致。
- 新皮肤：在 `Skins.ALL` 加一条、在 `Skin.ART` 里登记插画和缩略图，名字和简介补六种翻译。

## 小部件
- `CourseWidget.kt`：4×2 / 2×2，整张画成位图。排版、内容和行为照搬留白；颜色取当前皮肤。
- 刷新：没有后台服务，改用 `AlarmManager.set(RTC)`（不唤醒）排下一次：今天还有课就下一分钟，没课到明天 0 点；`WidgetTick` 接收闹钟和改时间 / 时区 / 更新安装广播。改课、改设置、换皮肤都会 `CourseWidgets.safeUpdate`。
- 小部件页（「我的 → 桌面小部件」）显示和桌面同一套画法的实时预览（`CourseWidgets.preview`），支持一键添加（`requestPinAppWidget`）。液态玻璃样式下有「玻璃颜色」（跟随壁纸 / 跟随皮肤）和「透明度」滑块：拖动时预览立刻变，停 0.4 秒后重画桌面；跟随壁纸时预览底下垫一块壁纸主色渐变（App 读不到壁纸图本身）。
- 小部件液态玻璃样式（build-16）：玻璃底按「透明度」设置铺一层（SharedPreferences `widget` 的 `clear`，0–90%，默认 65% ≈ 26% 实；build-15 的 52% / 62% 在照片壁纸上像灰板），字区再叠一层局部淡底（玻璃越透越实一点）+ `glassShine`；字只有一道 0.7px 细影（深色玻璃暗影、浅色玻璃淡白影，强度随透明度），不加模糊光晕。颜色来源 `tint`：「跟随壁纸」（默认，`WallTint.kt`：`WallpaperManager.getWallpaperColors` Android 8.1+，12+ 加 Material You 强调色和深浅提示 → 配出一份 `Pal`，不铺皮肤插画）/「跟随皮肤」（原来的皮肤底色 + 很淡的皮肤插画）。读不到壁纸颜色时退回皮肤。壁纸变了：App 进程活着时 `WallTint.listen`（`addOnColorsChangedListener`，在 `ZhishiApp` 里注册）立刻重画，另外每次小部件重画都重新读（签名里带壁纸颜色）。小部件看不到身后壁纸的像素，做不了真折射——对用户也这么说。经典样式不受 tint / clear 影响。

## 用户的决定（不要推翻）
- 课表节次时间由用户自己编辑，不内置东北大学作息预设；导入只在教务数据返回时间时才写时间。
- 不做 WakeUp 分享口令导入；界面上也不要出现「WakeUp」字样（CSV 导入叫「Excel / CSV 导入」）。`TourAndUpdaterTest.noWakeUpWording` 会查。
- 教务导入页不默认任何学校（包括东北大学）：打开时搜索框为空并聚焦，东大只在搜到时出现；用户选过一次后才在「上次用的」显示（SharedPreferences `jwimport` 的 `last_*`）。
- 东大（金智）教务导入已在真实账号验证通过，改其他教务时不要动它的逻辑。
- 新 UI 文案六种语言都要补。

## 教务导入现状（从留白继承，逻辑未改）
- 真实登录验证：仅东大金智。其他全部只有构造的样例页面 / JSON 单测（`JwTest`、`JwAdaptersTest`、`JwFetchTest`、`EamsFetchTest`，样例在 `app/src/test/resources/jw/`），没有真实账号测过。对外说明时如实讲，不夸大。
- 解析在 `Jw.kt` 和 `JwAdapters.kt`，取数脚本在 `JwScript.kt` 的 `jwScript`，导入界面在 `WebImport.kt`，内置浏览器设置在 `JwWebView.kt`。JS 桥对象名 `ZhishiJw`（测试的 harness.js 同名）。
- 所有教务类型都是「登录后直接点导入」：脚本按类型直接请求课表接口，请求不到再读当前页面和 iframe/frame。每个请求 20 秒超时、整次 90 秒看门狗、页面跳转中点导入会等加载完、过期结果丢掉。失败分「登录未完成 / 没找到课表 / 解析为空 / 请求失败 / 超时」，有「重试」和「复制诊断信息」。
- 树维 eams：脚本自己 GET `courseTableForStd.action` 取 ids / 学期 id，再 POST `courseTableForStd!courseTable.action`；电子科大读不到学期 id 时按日期推（483 起每学期 +20）。冒烟测试用 `tools/fake_eams.py` 在模拟器上端到端跑。都是手写样例。
- 已知问题：电子科大统一认证二次认证页在内置浏览器里可能空白（没有真实账号确认修好）；先让用户试「电脑版」。
- 学校搜索：`assets/schools.tsv`，由 `tools/build_schools.py` 生成（来源 s = shiguang_warehouse，b = baoozak/timetable，m = 手动核对）。列表网址 / 类型都没验证过，界面上有提示。
- 第三方参考只用 MIT / Apache-2.0 代码并保留署名（THIRD_PARTY_NOTICES.md 以及 `Jw.kt`/`JwAdapters.kt` 文件头）；不要复制 GPL 项目代码。
- 开学日期不从教务读取，导入后让用户确认。

## 冒烟测试钩子（debug 和 qa 版，`TestHooks.kt`）
- 钩子由 `BuildConfig.HOOKS` 开关（debug、qa 为 true，release 为 false），不要再用 `BuildConfig.DEBUG` 判断。
- `qa` 构建类型：`initWith(release)`——和正式版一样 R8 混淆 / 资源压缩，但用调试签名、带测试钩子（源码用 `src/debug`）。`build.yml` 每次都打 `out/test-qa.apk`，冒烟测试最后跑 `QUICK=1 tools/crashhunt.sh`（今天的课上完 / 正好下课 / 老数据 / 空，冷启动两次 + 两个导入入口）。混淆相关的崩溃只有在 qa / release 上才看得到。
- `tools/crashhunt.sh` 完整版（8 种时刻 + 8 种导入方式 + 强制更新弹窗）用 `.github/workflows/crashhunt.yml`，**只手动触发**（workflow_dispatch），产物 artifact `hunt`（`crashhunt-<标签>.txt` 里是 FATAL EXCEPTION 堆栈）。模拟器是英文系统，脚本先 `--es setlang zh`（会让进程自己退出重启，单独一步）。`--es seedat after|before|between|ending|offday|tiny` 按「现在」造课表，`--ez oldformat true` 写坏的老格式数据，`--ei fakeneed N` 假装查到新版。所有 workflow job 都有 `timeout-minutes`。
- `--ez notodos true` 清空待办（截空状态）；`--es wtint wall|skin`、`--ei wclear N` 小部件玻璃颜色 / 透明度；`--es wallpaper dark|light` 换模拟器壁纸（调试版 manifest 才有 SET_WALLPAPER）。
- `--ez seed true` 放示例课程（含一节「正在上」的课）和几条示例待办；`--es setlang zh|en|…` 换语言；`--es skin <id> --ei mode 0|1|2` 换皮肤；`--es open table:N|todo:N|calendar|settings:N|me|widget|about` 打开页面（`todo:1` 新建待办、`todo:2` 编辑第一条）（`table:1` 教务导入、`table:2` 学期、`table:3` 加课、`table:4` 改课、`table:5` 开学日期、`table:7` 每节时间、`table:8` 导入菜单）；`--es jwkind/jwurl --ez jwauto` 网页导入；`--es jwq` 预填学校搜索；`--es ocr <文件>` 识别截图。

## 工作习惯
- **Compose：不要在 Row / Column / Box 等 inline 布局的内容 lambda 里用 `return@Row` 这类带标签的 return 提前返回。** 现在是 Kotlin 1.9.22 + Compose 编译器 1.5.8 + 运行时 1.7（BOM 2024.09.03），这种写法会让组栈错位，首帧组合 `ArrayIndexOutOfBoundsException`（`IntStack.peek2` / `ComposerImpl.endRoot`）。build-15/16 「今天的课上完后」打开 App / 点小部件 / 导入课表后回课表页一直闪退就是 `NowStrip` 里的一句 `return@Row`（build-17 修）。用 if / else 或拆成小 composable；`RobustnessTest.noEarlyReturnFromInlineLayouts` 会扫源码。
- 声称完成前先让 CI 绿、看冒烟截图。
- 没验证过的功能不说已完成。
