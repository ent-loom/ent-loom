import com.entloom.meta.annotations.EntField;
import com.entloom.meta.annotations.EntEntity;
import com.entloom.meta.annotations.EntIndex;
import com.entloom.meta.enums.EntFieldKind;
import com.entloom.meta.annotations.meta.EntMetaDateTime;
import com.entloom.meta.annotations.meta.EntMetaEnum;
import com.entloom.meta.annotations.meta.EntMetaId;
import com.entloom.meta.annotations.meta.EntMetaMedia;
import com.entloom.meta.annotations.meta.EntMetaNumber;
import com.entloom.meta.annotations.EntRelation;
import com.entloom.meta.annotations.meta.EntMetaText;
import com.entloom.meta.enums.role.DateTimeRole;
import com.entloom.meta.enums.role.EnumRole;
import com.entloom.meta.enums.role.MediaRole;
import com.entloom.meta.enums.role.NumberRole;
import com.entloom.meta.enums.role.RefIdRole;
import com.entloom.meta.enums.role.TextRole;

/**
 * EntEntity 内容示例。
 */
@EntEntity(
        entity = "content_article",
        service = "content-center",
        label = "文章",
        description = "内容文章语义模型",
        defaultLabelFields = {"title"},
        plannedVolume = 500000
)
@EntIndex(name = "idx_content_article_creator", fields = {"creatorId"})
@EntIndex(name = "idx_content_article_stage_publish_time", fields = {"publishStage", "publishAtEpochSeconds"})
public class DemoEntArticle {

    @EntField(EntFieldKind.ID)
    @EntMetaId
    private Long id;

    @EntField(EntFieldKind.REF_ID)
    @EntRelation(role = RefIdRole.GENERIC, targetService = "account-center", targetEntity = "account")
    private Long creatorId;

    @EntField(EntFieldKind.TEXT)
    @EntMetaText(TextRole.GENERIC)
    private String title;

    @EntField(EntFieldKind.MEDIA)
    @EntMetaMedia(MediaRole.IMAGE)
    private String coverImageUrl;

    @EntField(EntFieldKind.RICH_CONTENT)
    private String bodyContent;

    @EntField(EntFieldKind.ENUM)
    @EntMetaEnum(value = EnumRole.GENERIC, valueType = EntMetaEnum.ValueType.STRING)
    private PublishStage publishStage;

    @EntField(EntFieldKind.NUMBER)
    @EntMetaNumber(NumberRole.GENERIC)
    private Integer qualityScore;

    @EntField(EntFieldKind.DATETIME)
    @EntMetaDateTime(value = DateTimeRole.GENERIC, encoding = EntMetaDateTime.TimeEncoding.EPOCH_SECONDS)
    private Long publishAtEpochSeconds;

    public enum PublishStage {
        DRAFT,
        REVIEWING,
        PUBLISHED
    }
}
