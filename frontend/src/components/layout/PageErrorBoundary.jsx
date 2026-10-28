import { Component } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Button, EmptyState } from '@/components/ui';

/** 본문 화면에서 난 오류를 본문 영역에만 보여 줍니다. 사이드바·상단바는 그대로 둡니다. */
class PageErrorBoundary extends Component {
  state = { error: null };

  componentDidUpdate(previous) {
    // 다른 메뉴로 옮기면 다시 그려 봅니다.
    if (this.state.error && previous.resetKey !== this.props.resetKey) this.setState({ error: null });
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <EmptyState
        icon={AlertTriangle}
        title="화면을 불러오지 못했습니다"
        description="새 버전이 배포됐거나 연결이 잠시 끊겼을 수 있습니다."
        action={<Button onClick={() => window.location.reload()}>새로고침</Button>}
        className="py-24"
      />
    );
  }
}

// 정적 메서드를 대입으로 둡니다: Tailwind가 소스의 단어를 클래스로 읽어 배포 CSS가 바뀌지 않게 합니다.
PageErrorBoundary.getDerivedStateFromError = (error) => ({ error });

export default PageErrorBoundary;
