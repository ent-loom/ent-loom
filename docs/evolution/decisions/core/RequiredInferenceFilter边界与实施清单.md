# RequiredInferenceFilter 退役说明

> 状态：Superseded

实体字段必填推断曾作为项目级扩展方案进行评估，但在外部输入契约重构中被移除。当前代码不再提供 `RequiredInferenceFilter`、`RequiredInferenceContribution`、`RequiredInferenceValue` 或相关元数据裁决链。

普通 CRUD 的请求输入要求统一配置在 `ent.loom.crud.contracts`：

```yaml
ent:
  loom:
    crud:
      contracts:
        create:
          input-required-fields:
            product: [name, price]
        update:
          forbidden-fields:
            product: [id]
```

该配置只表达稳定的外部请求边界，不推断实体最终非空，不读取请求上下文、数据库状态或租户状态。未配置资源契约时不推断创建必填，也不能将 CRUD 输入配置投影为通用 Meta 属性。

替代决策见 [CRUD 外部输入契约与统一校验](实体必填约束与统一校验.md) 和 [外部输入契约与实体校验边界](../crud/外部输入契约与实体校验边界.md)。
