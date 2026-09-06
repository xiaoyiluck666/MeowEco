# MeowEco Economy 26.10.1

本补丁版本让货币政策报告完整支持中英文及自定义措辞，并发布此前已完成的报告实现。

## Added

- 为 `/meco policy report` 增加可自定义的英文和简体中文模板。
- 增加富人税状态和去向的本地化标签。

## Changed

- 报告跟随 `messages.language`，编辑 `plugins/MeowEco/lang/` 后执行 `/meco reload` 即可生效。
- 发布版本提升至 `26.10.1`。

## Fixed

- 移除货币政策报告中的硬编码英文标签。
