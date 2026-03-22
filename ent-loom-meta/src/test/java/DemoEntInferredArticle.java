import com.entloom.meta.annotations.EntEntity;

import java.time.LocalDate;

/**
 * 纯推断示例：内容模型。
 */
@EntEntity(
        entity = "inferred_content_article",
        service = "content-center",
        label = "推断文章",
        description = "字段类型 + 字段名基线推断",
        defaultLabelFields = {"title"},
        plannedVolume = 500000
)
public class DemoEntInferredArticle {

    private Long id;

    private Long creatorId;

    private String title;

    private String description;

    private String remark;

    private String secretKey;

    private String sourceCode;

    private String coverImageUrl;

    private String galleryImageUrls;

    private String attachmentFileUrl;

    private String payloadJson;

    private Integer qualityLevel;

    private String sourceMode;

    private LocalDate publishDate;
}
