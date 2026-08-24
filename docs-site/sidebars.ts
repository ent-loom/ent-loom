import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  userSidebar: [
    'index',
    {
      type: 'category',
      label: '使用指南',
      collapsed: false,
      items: [
        'guides/index',
        'guides/开发环境与JDK管理',
        {
          type: 'category',
          label: 'CRUD',
          items: [
            'guides/crud/index',
            'guides/crud/开发指南',
            'guides/crud/业务集成模板',
            'guides/crud/导出展示值配置',
          ],
        },
        {
          type: 'category',
          label: 'Meta',
          items: [
            'guides/meta/index',
            'guides/meta/Meta优先指南',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: '领域入口',
      collapsed: false,
      items: [
        'domains/crud/index',
        'domains/meta/index',
      ],
    },
    {
      type: 'category',
      label: '当前架构',
      collapsed: false,
      items: [
        'architecture/index',
        'architecture/架构总览',
        'architecture/core/index',
        {
          type: 'category',
          label: 'Core Contract',
          items: [
            'architecture/core/组件边界与依赖规则',
            'architecture/core/元数据约定与裁决契约',
            'architecture/core/governance/治理架构',
            'architecture/core/governance/治理流水线',
            'architecture/core/governance/治理核心架构',
            'architecture/core/subject/执行上下文与主体',
            'architecture/core/meta/分层与运行模型',
            'architecture/core/meta/元数据解析引擎',
            'architecture/core/meta/运行时适配器',
          ],
        },
        {
          type: 'category',
          label: 'CRUD 组件',
          items: [
            'architecture/components/index',
            'architecture/components/crud/index',
            'architecture/components/crud/架构总览',
            'architecture/components/crud/运行时架构',
            'architecture/components/crud/HTTP契约',
            'architecture/components/crud/查询命令协议',
            'architecture/components/crud/强类型边界',
            'architecture/components/crud/查询',
            'architecture/components/crud/命令',
            'architecture/components/crud/统计',
            'architecture/components/crud/导入',
            'architecture/components/crud/导出',
            'architecture/components/crud/任务文件',
            'architecture/components/crud/默认引擎',
            'architecture/components/crud/运行时注册表',
          ],
        },
      ],
    },
  ],
};

export default sidebars;
