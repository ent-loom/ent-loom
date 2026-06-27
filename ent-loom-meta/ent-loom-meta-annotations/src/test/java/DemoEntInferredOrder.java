import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.enums.EntFieldKind;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 纯推断示例：订单模型。
 */
@EntEntity(
        entity = "inferred_trade_order",
        service = "trade-center",
        label = "推断订单",
        description = "通过字段命名约定推断 EntField 语义",
        defaultLabelFields = {"orderNo"},
        plannedVolume = 2000000
)
public class DemoEntInferredOrder {

    private Long id;

    private Long ownerId;

    private String orderNo;

    @EntField(value = EntFieldKind.ENUM, label = "订单状态")
    private OrderStatus orderStatus;

    private String orderType;

    private BigDecimal payAmount;

    private String extensionJson;

    private String coverImageUrl;

    private String detailFilePath;

    private Boolean isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime startTime;

    public enum OrderStatus {
        WAIT_PAY,
        PAID,
        CANCELED
    }
}
