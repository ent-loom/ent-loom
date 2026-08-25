import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'ent-loom 文档中心',
  tagline: '面向实体编程的业务友好型框架',
  favicon: 'img/favicon.svg',
  url: 'https://docs.ent-loom.com',
  baseUrl: '/',
  organizationName: 'ent-loom',
  projectName: 'ent-loom',
  onBrokenLinks: 'throw',
  onBrokenAnchors: 'throw',
  i18n: {
    defaultLocale: 'zh-Hans',
    locales: ['zh-Hans'],
  },
  presets: [
    [
      'classic',
      {
        docs: {
          path: '../docs',
          routeBasePath: 'docs',
          sidebarPath: './sidebars.ts',
          showLastUpdateTime: false,
          breadcrumbs: true,
          include: ['**/*.md', '**/*.mdx'],
          exclude: [
            '**/archive/**',
            '**/implementation/**',
            '**/*.txt',
            '**/drafts/**',
            '**/*.draft.md',
          ],
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],
  themes: ['@docusaurus/theme-mermaid'],
  markdown: {
    mermaid: true,
    hooks: {
      onBrokenMarkdownLinks: 'throw',
    },
  },
  themeConfig: {
    image: 'img/ent-loom-social-card.svg',
    navbar: {
      title: 'ent-loom',
      logo: {
        alt: 'ent-loom 标志',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'userSidebar',
          label: '文档',
          position: 'left',
        },
        {
          href: 'https://github.com/ent-loom/ent-loom',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: '文档',
          items: [
            {
              label: '文档中心',
              to: '/docs',
            },
            {
              label: 'CRUD 领域',
              to: '/docs/domains/crud',
            },
            {
              label: 'DDL 领域',
              to: '/docs/domains/ddl',
            },
            {
              label: 'Meta 领域',
              to: '/docs/domains/meta',
            },
          ],
        },
        {
          title: '项目',
          items: [
            {
              label: 'GitHub',
              href: 'https://github.com/ent-loom/ent-loom',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} ent-loom`,
    },
    prism: {
      theme: {
        plain: {
          color: '#263238',
          backgroundColor: '#f4f7f6',
        },
        styles: [],
      },
      darkTheme: {
        plain: {
          color: '#e6edf3',
          backgroundColor: '#172126',
        },
        styles: [],
      },
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
