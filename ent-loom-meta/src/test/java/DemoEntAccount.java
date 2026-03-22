import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.enums.EntFieldKind;
import com.entloom.meta.annotations.meta.EntMetaDateTime;
import com.entloom.meta.annotations.meta.EntMetaEnum;
import com.entloom.meta.annotations.meta.EntMetaFlag;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.annotations.meta.EntMetaText;
import com.entloom.meta.enums.role.DateTimeRole;
import com.entloom.meta.enums.role.EnumRole;
import com.entloom.meta.enums.role.FlagRole;
import com.entloom.meta.enums.role.TextRole;

import java.time.LocalDateTime;

/**
 * EntEntity 账号示例。
 */
@EntEntity(
        entity = "account",
        service = "account-center",
        label = "账号",
        description = "平台账号基础信息",
        defaultLabelFields = {"nickname", "mobile"},
        plannedVolume = 300000
)
public class DemoEntAccount {

    @EntField(EntFieldKind.ID)
    @EntMetaId
    private Long id;

    @EntField(EntFieldKind.TEXT)
    @EntMetaText(TextRole.GENERIC)
    private String nickname;

    @EntField(EntFieldKind.TEXT)
    @EntMetaText(TextRole.GENERIC)
    private String mobile;

    @EntField(EntFieldKind.ENUM)
    @EntMetaEnum(value = EnumRole.STATUS, valueType = EntMetaEnum.ValueType.STRING)
    private AccountStatus status;

    @EntField(EntFieldKind.FLAG)
    @EntMetaFlag(FlagRole.ENABLED)
    private Boolean enabled;

    @EntField(EntFieldKind.DATETIME)
    @EntMetaDateTime(DateTimeRole.CREATED_TIME)
    private LocalDateTime createdAt;

    public enum AccountStatus {
        PENDING,
        ACTIVE,
        DISABLED
    }
}
