# PROJECT_CONTEXT_LATEST

- 当前目标：维护 `meoweco-paper` 对 `Paper 26.1.1` 的支持，并继续优化中英文 Wiki 的产品表达，方便服主快速理解插件价值和接入方式。
- 最近完成：进一步补强中英文 Wiki，对格式化显示的膨胀场景优势、富人税的经济平衡用途、多货币与自定义货币特色、以及 Paper/Vault/PlaceholderAPI/TrMenu 的服主接入方式做了更明确说明，并新增了 `raw / formatted / fixed / short` 的余额显示示例；同时将“显示格式化余额”章节前置并让 TrMenu 章节紧随其后；同步更新 `CHANGELOG.md`。
- 关键改动文件：`MeowEco.wiki/WIKI_CN.md`、`MeowEco.wiki/WIKI_EN.md`、`CHANGELOG.md`。
- 待办 Top3：1) 如需发版，可基于现有 Wiki 提炼一页服主宣传页；2) 如需继续补文档，可新增完整 TrMenu 菜单案例页；3) 如需更强验证，可做真实服务器上的菜单联动烟测。
- 风险与回滚点：本轮仅改文档，不影响代码与构建；TrMenu 部分仍采用谨慎表述，避免把外部简写动作环境误写成仓库内置强绑定实现。
