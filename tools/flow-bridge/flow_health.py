"""Same editor-based health contract used by NEXUS; no generation or interactive login."""
import time
from typing import Any


async def gflow_auth_status(profile: str = 'nexus') -> dict[str, Any]:
    """Check the owned Flow editor session; never generates or opens interactive login."""
    if profile != 'nexus':
        return {'status':'unavailable','source':'flow_editor','reason':'unsupported_profile','profile':profile}
    from flow_session import cached, renew
    old=cached()
    try:
        value=old or await renew()
    except Exception:
        value={'status':'error','checked_at':time.time(),'reason':'verification_error'}
    reason=value.get('reason')
    allowed={'browser_rejected','password_required','verification_required','account_selection_required','google_signin','page_not_ready'}
    return {'status':value['status'],'profile':'nexus','source':'flow_editor',
            'checked_at':value['checked_at'],'age_seconds':max(0,round(time.time()-value['checked_at'])),
            'cached':bool(old),'refreshed':value.get('refreshed') is True,
            'reason':reason if reason in allowed else 'verification_error' if value['status']=='error' else None,
            'balance_status':'not_checked'}
