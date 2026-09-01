import React, {
  useEffect,
  useRef,
  useState,
  type PointerEvent,
  type ReactNode,
} from 'react';
import OriginalMermaid from '@theme-original/Mermaid';
import type {Props} from '@theme/Mermaid';

import styles from './styles.module.css';

const MIN_SCALE = 0.25;
const MAX_SCALE = 3;
const SCALE_STEP = 0.25;

type Pan = {
  x: number;
  y: number;
};

function clampScale(scale: number): number {
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));
}

function createPreviewSvg(svg: SVGSVGElement): string {
  const clone = svg.cloneNode(true) as SVGSVGElement;
  const {width, height} = clone.viewBox.baseVal;

  if (width > 0 && height > 0) {
    clone.setAttribute('width', String(width));
    clone.setAttribute('height', String(height));
    clone.style.maxWidth = 'none';
  }

  return clone.outerHTML;
}

function MermaidPreview({
  svgMarkup,
  onClose,
}: {
  svgMarkup: string;
  onClose: () => void;
}): ReactNode {
  const [scale, setScale] = useState(1);
  const [fitScale, setFitScale] = useState(1);
  const [pan, setPan] = useState<Pan>({x: 0, y: 0});
  const [isDragging, setIsDragging] = useState(false);
  const stageRef = useRef<HTMLDivElement>(null);
  const diagramRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<{startX: number; startY: number; pan: Pan} | null>(null);

  useEffect(() => {
    const stage = stageRef.current;
    const svg = diagramRef.current?.querySelector('svg');
    if (!stage || !svg) {
      return;
    }

    const svgRect = svg.getBoundingClientRect();
    const availableWidth = stage.clientWidth * 0.92;
    const availableHeight = stage.clientHeight * 0.92;
    const nextFitScale = clampScale(
      Math.min(1, availableWidth / svgRect.width, availableHeight / svgRect.height),
    );
    setFitScale(nextFitScale);
    setScale(nextFitScale);
  }, [svgMarkup]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', onKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [onClose]);

  const resetView = () => {
    setScale(fitScale);
    setPan({x: 0, y: 0});
  };

  const zoomBy = (delta: number) => {
    setScale((currentScale) => clampScale(currentScale + delta));
  };

  const onWheel = (event: React.WheelEvent<HTMLDivElement>) => {
    event.preventDefault();
    zoomBy(event.deltaY < 0 ? SCALE_STEP : -SCALE_STEP);
  };

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) {
      return;
    }
    event.currentTarget.setPointerCapture(event.pointerId);
    setIsDragging(true);
    dragRef.current = {
      startX: event.clientX,
      startY: event.clientY,
      pan,
    };
  };

  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    if (!drag) {
      return;
    }
    setPan({
      x: drag.pan.x + event.clientX - drag.startX,
      y: drag.pan.y + event.clientY - drag.startY,
    });
  };

  const stopDragging = (event: PointerEvent<HTMLDivElement>) => {
    setIsDragging(false);
    dragRef.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  return (
    <div className={styles.backdrop} role="presentation" onMouseDown={onClose}>
      <section
        className={styles.dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby="mermaid-preview-title"
        onMouseDown={(event) => event.stopPropagation()}>
        <h2 id="mermaid-preview-title" className={styles.visuallyHidden}>
          流程图预览
        </h2>
        <div className={styles.toolbar}>
          <button type="button" title="缩小" aria-label="缩小" onClick={() => zoomBy(-SCALE_STEP)}>
            -
          </button>
          <output aria-live="polite">{Math.round(scale * 100)}%</output>
          <button type="button" title="放大" aria-label="放大" onClick={() => zoomBy(SCALE_STEP)}>
            +
          </button>
          <button type="button" title="重置视图" aria-label="重置视图" onClick={resetView}>
            ↺
          </button>
          <button type="button" className={styles.closeButton} title="关闭" aria-label="关闭" onClick={onClose}>
            ×
          </button>
        </div>
        <div
          ref={stageRef}
          className={styles.stage}
          onWheel={onWheel}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={stopDragging}
          onPointerCancel={stopDragging}
          style={{cursor: isDragging ? 'grabbing' : 'grab'}}>
          <div
            ref={diagramRef}
            className={styles.diagram}
            style={{transform: `translate(${pan.x}px, ${pan.y}px) scale(${scale})`}}
            // eslint-disable-next-line react/no-danger
            dangerouslySetInnerHTML={{__html: svgMarkup}}
          />
        </div>
      </section>
    </div>
  );
}

export default function Mermaid(props: Props): ReactNode {
  const diagramRef = useRef<HTMLDivElement>(null);
  const [svgMarkup, setSvgMarkup] = useState<string | null>(null);

  const openPreview = () => {
    const svg = diagramRef.current?.querySelector('svg');
    if (svg) {
      setSvgMarkup(createPreviewSvg(svg));
    }
  };

  return (
    <>
      <div ref={diagramRef} className={styles.figure}>
        <OriginalMermaid {...props} />
        <button
          type="button"
          className={styles.openButton}
          title="放大流程图"
          aria-label="放大流程图"
          onClick={openPreview}>
          ↗
        </button>
      </div>
      {svgMarkup && <MermaidPreview svgMarkup={svgMarkup} onClose={() => setSvgMarkup(null)} />}
    </>
  );
}
