import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.enums.EntFieldKind;
import com.entloom.meta.annotations.meta.EntMetaDateTime;
import com.entloom.meta.annotations.meta.EntMetaEnum;
import com.entloom.meta.annotations.meta.EntMetaFlag;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.annotations.meta.EntMetaJson;
import com.entloom.meta.annotations.meta.EntMetaNumber;
import com.entloom.meta.annotations.meta.EntMetaRefId;
import com.entloom.meta.annotations.meta.EntMetaText;
import com.entloom.meta.enums.role.DateTimeRole;
import com.entloom.meta.enums.role.EnumRole;
import com.entloom.meta.enums.role.FlagRole;
import com.entloom.meta.enums.role.JsonRole;
import com.entloom.meta.enums.role.NumberRole;
import com.entloom.meta.enums.role.RefIdRole;
import com.entloom.meta.enums.role.TextRole;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * EntEntity 订单示例。
 */
@EntEntity(
        entity = "trade_order",
        service = "trade-center",
        label = "交易订单",
        description = "订单业务语义模型",
        defaultLabelFields = {"orderNo"},
        plannedVolume = 2000000
)
public class DemoEntOrder {

    @EntField(EntFieldKind.ID)
    @EntMetaId
    private Long id;

    @EntField(EntFieldKind.REF_ID)
    @EntMetaRefId(value = RefIdRole.TENANT, refService = "iam-center", refEntity = "tenant")
    private Long tenantId;

    @EntField(EntFieldKind.REF_ID)
    @EntMetaRefId(value = RefIdRole.GENERIC, refService = "account-center", refEntity = "account")
    private Long ownerId;

    @EntField(EntFieldKind.TEXT)
    @EntMetaText(TextRole.GENERIC)
    private String orderNo;

    @EntField(EntFieldKind.ENUM)
    @EntMetaEnum(EnumRole.STATUS)
    private OrderStatus orderStatus;

    @EntField(EntFieldKind.NUMBER)
    @EntMetaNumber(NumberRole.MONEY)
    private BigDecimal payAmount;

    @EntField(EntFieldKind.JSON_DOC)
    @EntMetaJson(JsonRole.GENERIC)
    private String extensionJson;

    @EntField(EntFieldKind.FLAG)
    @EntMetaFlag(FlagRole.SOFT_DELETE)
    private Boolean deleted;

    @EntField(EntFieldKind.DATETIME)
    @EntMetaDateTime(DateTimeRole.CREATED_TIME)
    private LocalDateTime createdAt;

    @EntField(EntFieldKind.DATETIME)
    @EntMetaDateTime(DateTimeRole.UPDATED_TIME)
    private LocalDateTime updatedAt;

    public enum OrderStatus {
        WAIT_PAY,
        PAID,
        CLOSED
    }
}
