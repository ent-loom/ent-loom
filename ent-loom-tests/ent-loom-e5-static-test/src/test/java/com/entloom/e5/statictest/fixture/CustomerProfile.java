package com.entloom.e5.statictest.fixture;

import com.entloom.base.common.OptionalBoolean;
import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.api.enums.CrudIdPolicy;
import com.entloom.ddl.annotations.EntDdlEntity;
import com.entloom.ddl.annotations.EntDdlField;
import com.entloom.ddl.annotations.EntDdlIndex;
import com.entloom.ddl.enums.GenerationStrategy;
import com.entloom.doc.annotations.EntDocEntity;
import com.entloom.doc.annotations.EntDocField;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntIndex;
import com.entloom.meta.annotations.meta.EntMetaDateTime;
import com.entloom.meta.annotations.meta.EntMetaMedia;
import com.entloom.meta.annotations.meta.EntMetaNumber;
import com.entloom.meta.annotations.meta.EntMetaText;
import com.entloom.meta.enums.EntFieldKind;
import com.entloom.meta.enums.role.DateTimeRole;
import com.entloom.meta.enums.role.MediaRole;
import com.entloom.meta.enums.role.NumberRole;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** E5 静态与集成验收共用的客户档案实体样例。 */
@EntEntity(
    entity = "customer_profile",
    value = "客户档案",
    description = "客户档案",
    service = "customer-service"
)
@EntIndex(name = "uk_customer_profile_display_name", fields = {"displayName"}, unique = true)
@EntDdlEntity(table = "customer_profile", comment = "客户档案")
@EntDdlIndex(
    name = "uk_customer_profile_display_name",
    fields = {"display_name"},
    unique = OptionalBoolean.TRUE
)
@EntCrudEntity(
    name = "customer_profile",
    table = "customer_profile",
    idPolicy = CrudIdPolicy.EXPLICIT,
    ownerService = "customer-service"
)
@EntDocEntity(name = "客户档案", description = "客户档案")
public final class CustomerProfile {
    /** 数据库主键。 */
    @EntField(value = "标识", description = "主键")
    @EntDdlField(
        column = "id",
        nullable = OptionalBoolean.FALSE,
        primaryKey = OptionalBoolean.TRUE,
        generationStrategy = GenerationStrategy.AUTO_INCREMENT,
        comment = "主键"
    )
    @EntDocField(name = "标识", description = "主键")
    private Long id;

    /** 客户展示名称。 */
    @EntField(
        value = "显示名称",
        description = "客户展示名称",
        examples = {"张三"}
    )
    @EntMetaText(maxLength = 64)
    @EntDdlField(column = "display_name", length = 64, nullable = OptionalBoolean.FALSE, comment = "客户展示名称")
    @EntDocField(
        name = "显示名称",
        description = "客户展示名称",
        example = "张三",
        maxLength = 64
    )
    private String displayName;

    /** 客户信用额度。 */
    @EntField(
        value = "信用额度",
        description = "客户信用额度",
        examples = {"1000.00"}
    )
    @EntMetaNumber(value = NumberRole.MONEY, precision = 10, scale = 2)
    @EntDdlField(column = "credit_limit", precision = 10, scale = 2, nullable = OptionalBoolean.FALSE, comment = "客户信用额度")
    @EntDocField(
        name = "信用额度",
        description = "客户信用额度",
        example = "1000.00"
    )
    private BigDecimal creditLimit;

    /** 客户注册时间。 */
    @EntField(
        value = "注册时间",
        description = "客户注册时间"
    )
    @EntMetaDateTime(value = DateTimeRole.CREATED_TIME, encoding = EntMetaDateTime.TimeEncoding.ISO_LOCAL)
    @EntDdlField(column = "registered_at", nullable = OptionalBoolean.FALSE, comment = "客户注册时间")
    @EntDocField(name = "注册时间", description = "客户注册时间")
    private LocalDateTime registeredAt;

    /** 客户头像地址，作为 UI 图片字段验收样本。 */
    @EntField(
        kind = EntFieldKind.MEDIA,
        value = "头像",
        description = "客户头像地址"
    )
    @EntMetaMedia(value = MediaRole.IMAGE, pathMode = EntMetaMedia.PathMode.ABSOLUTE_URL, accept = {"image/png", "image/jpeg"})
    @EntDdlField(column = "avatar_url", length = 255, nullable = OptionalBoolean.TRUE, comment = "客户头像地址")
    @EntDocField(name = "头像", description = "客户头像地址")
    private String avatarUrl;
}
