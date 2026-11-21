# 第三方声明 / Third-party notices

知时的教务导入（继承自作者的另一个应用「留白」）参考并移植了以下开源项目的逻辑和数据。只用了 MIT 许可的部分；GPL 项目（如 Dawn-Course、sleepy、Saafo/uestc-coursetable-parser）没有复制任何代码，最多只核对过接口名。
`kqakqakqa/xiaoai-schedule-parser`（中国矿业大学新正方分支）没有许可证：只看了接口名和参数名（`xskbcx_cxXsgrkb.html`、`gnmkdm=N2151`、`kzlx=ck`、`xsdm`），没有复制代码。

## ShiGuangSchedule/shiguang_warehouse — MIT
- 来源：https://github.com/ShiGuangSchedule/shiguang_warehouse （读取时的提交 250c4f9）
- 用在：`app/src/main/java/com/zhishi/timetable/JwAdapters.kt`（正方网格/列表视图、新 URP 页面、青果「周次[节次]」、超星 sdpkkbList 接口与连排合并的解析思路）、
  `Jw.kt` 里强智的行标签节次与单双周字段、`WebImport.kt` 里超星的取数流程，以及树维 eams 直接请求课表的参数（`resources/UESTC/uestc.js`：
  `courseTableForStd!courseTable.action` 的 ignoreHead / setting.kind / project.id / semester.id / ids，电子科大学期 id 规律 483 起每学期 +20）、`Jw.kt` 树维解析里跳过「停课」记录；`JwScript.kt` 青果新版接口 `wsxk/xkjg.ckdgxsxdkchj_data10319.jsp?params=base64(xn=..&xq=..)`（`resources/HIST/hist_01.js`）；
  `app/src/main/assets/schools.tsv` 中来源标 `s` 的学校和入口网址（来自各校 `adapters.yaml` 的 `import_url`）。
- 拾光课程表主仓库 XingHeYuZhuan/shiguangschedule 为 Apache-2.0；本项目没有使用它的代码，只用了上面这个 MIT 的适配脚本仓库。

Copyright (c) 2025 星河欲转

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## baoozak/timetable — MIT
- 来源：https://github.com/baoozak/timetable （读取时的提交 e4b57df）
- 用在：`Jw.kt` 正方紧凑节次「0102」、`WebImport.kt` 正方备用接口 `xskbcx_cxXsgrkb.html`、`JwAdapters.kt` 南软 `table.tb_kcb` 与 `<hr>` 分隔；
  `app/src/main/assets/schools.tsv` 中来源标 `b` 的学校、网址和类型（`utils/schools.json`，类型按网址特征做了修正）。

Copyright (c) 2026 baoozak

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## NEU 教务课表导出脚本（greasyfork 573005）— MIT
- 东北大学金智教务的接口参考，见 `Jw.kt` 开头说明（此前版本已有）。
