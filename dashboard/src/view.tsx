import { createContext, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';

type View = 'plain' | 'technical';

interface ViewState {
  technical: boolean;
  setView: (view: View) => void;
}

const ViewContext = createContext<ViewState>({ technical: false, setView: () => {} });

function initialView(): View {
  try {
    return localStorage.getItem('view') === 'technical' ? 'technical' : 'plain';
  } catch {
    return 'plain'; // storage can be blocked
  }
}

/**
 * Remembers whether the page should use plain English or the real technical names.
 * React context lets any component read this without passing it down through every parent.
 */
export function ViewProvider({ children }: { children: ReactNode }) {
  const [view, setView] = useState<View>(initialView);

  useEffect(() => {
    try {
      localStorage.setItem('view', view);
    } catch {
      // ignore
    }
  }, [view]);

  return <ViewContext.Provider value={{ technical: view === 'technical', setView }}>{children}</ViewContext.Provider>;
}

export const useView = (): ViewState => useContext(ViewContext);

/** Shows the plain-English wording, or the technical wording when the Technical view is on. */
export function Term({ plain, tech }: { plain: string; tech: string }) {
  const { technical } = useView();
  return <>{technical ? tech : plain}</>;
}
