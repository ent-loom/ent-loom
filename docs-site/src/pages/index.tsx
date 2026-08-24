import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';

import styles from './index.module.css';

type Entry = {
  eyebrow: string;
  title: string;
  description: string;
  to: string;
  tone: 'teal' | 'amber' | 'blue';
};

const entries: Entry[] = [
  {
    eyebrow: '业务接入',
    title: 'CRUD 开发指南',
    description: '从当前 CRUD 能力、治理边界和业务集成模板开始接入。',
    to: '/docs/guides/crud',
    tone: 'teal',
  },
  {
    eyebrow: '实体建模',
    title: 'Meta 优先指南',
    description: '理解属性来源、覆盖规则和 Meta-first 的依赖选择。',
    to: '/docs/guides/meta/Meta优先指南',
    tone: 'amber',
  },
  {
    eyebrow: '系统理解',
    title: '系统架构总览',
    description: '先掌握建模主链、执行主链和模块之间的职责边界。',
    to: '/docs/architecture/架构总览',
    tone: 'blue',
  },
];

function EntryCard({entry}: {entry: Entry}): ReactNode {
  return (
    <Link className={clsx(styles.entry, styles[entry.tone])} to={entry.to}>
      <span className={styles.entryEyebrow}>{entry.eyebrow}</span>
      <Heading as="h2">{entry.title}</Heading>
      <span className={styles.entryDescription}>{entry.description}</span>
      <span className={styles.entryArrow} aria-hidden="true">→</span>
    </Link>
  );
}

export default function Home(): ReactNode {
  return (
    <Layout title="文档中心" description="ent-loom 当前主线文档中心">
      <main>
        <section className={styles.intro}>
          <div className={styles.introInner}>
            <div className={styles.kicker}>当前主线文档</div>
            <Heading as="h1">ent-loom 文档中心</Heading>
            <p className={styles.lead}>
              面向实体编程的业务友好型框架。这里集中呈现当前代码与稳定契约对应的指南、领域入口和架构参考。
            </p>
            <div className={styles.actions}>
              <Link className="button button--primary button--lg" to="/docs">
                进入文档
              </Link>
              <Link className="button button--secondary button--lg" to="/docs/guides/开发环境与JDK管理">
                查看开发环境
              </Link>
            </div>
          </div>
        </section>

        <section className={styles.content}>
          <div className={styles.contentHeader}>
            <div>
              <div className={styles.sectionLabel}>推荐入口</div>
              <Heading as="h2">从当前问题开始</Heading>
            </div>
            <span className={styles.status}>Current · 主线文档</span>
          </div>
          <div className={styles.entries}>
            {entries.map((entry) => <EntryCard key={entry.to} entry={entry} />)}
          </div>

          <div className={styles.lowerGrid}>
            <section className={styles.listSection}>
              <div className={styles.sectionLabel}>完整导航</div>
              <Heading as="h2">按文档层次浏览</Heading>
              <div className={styles.linkList}>
                <Link to="/docs/domains/crud">CRUD 领域总览 <span aria-hidden="true">→</span></Link>
                <Link to="/docs/domains/meta">Meta 领域总览 <span aria-hidden="true">→</span></Link>
                <Link to="/docs/architecture/core">Core Contract <span aria-hidden="true">→</span></Link>
                <Link to="/docs/architecture/components/crud">CRUD 当前架构 <span aria-hidden="true">→</span></Link>
              </div>
            </section>
            <section className={styles.compatibility}>
              <div className={styles.sectionLabel}>运行边界</div>
              <Heading as="h2">当前兼容性口径</Heading>
              <p>完整 Maven Reactor 当前按 JDK 21+ 与 Spring Boot 3.5 主线验证。</p>
              <p className={styles.muted}>Core / Java 8、Boot 2 与 Boot 4 仍属于目标线路，详见兼容性决策。</p>
              <Link to="/docs/evolution/decisions/core/Java运行时与Spring兼容性">
                查看兼容性边界 <span aria-hidden="true">→</span>
              </Link>
            </section>
          </div>
        </section>
      </main>
    </Layout>
  );
}
