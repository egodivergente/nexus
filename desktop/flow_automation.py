"""Durable host-owned Flow queue. Recovery is read-only, never a new submit."""
from contextlib import contextmanager
import json
import math
from pathlib import Path
import threading
import time

MAX_RESULTS = 120
DEFAULT_POLICY = {'max_results':120, 'max_total_results':120, 'max_pending':120, 'max_credits':1000,
                  'allow_unknown_cost':False, 'paused':False}
START_LOCK = threading.RLock()
READ_LOCK = threading.RLock()
HELD = threading.local()
RUNNERS = {}
INITIALIZED = set()


@contextmanager
def profile_gate(runtime, timeout=15):
    """OS lock survives process death; shared across data directories and windows."""
    import os
    if getattr(HELD,'depth',0):
        yield
        return
    folder = Path(runtime) / 'jobs'
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / 'nexus-automation.lock'
    deadline = time.monotonic() + timeout
    if not READ_LOCK.acquire(timeout=timeout):
        raise RuntimeError('Flow está ocupado. No se ha enviado esta operación.')
    try:
        with path.open('a+b') as handle:
            handle.seek(0, 2)
            if handle.tell() == 0:
                handle.write(b'0'); handle.flush()
            while True:
                try:
                    handle.seek(0)
                    if os.name == 'nt':
                        import msvcrt
                        msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
                    else:
                        import fcntl
                        fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                    break
                except OSError:
                    if time.monotonic() >= deadline:
                        raise RuntimeError('Flow está ocupado. No se ha enviado esta operación.') from None
                    time.sleep(.1)
            HELD.depth=1
            try:
                yield
            finally:
                HELD.depth=0
                handle.seek(0)
                if os.name == 'nt':
                    msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
                else:
                    fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
    finally:
        READ_LOCK.release()


@contextmanager
def database(provider, data):
    db = provider.ledger(data)
    db.execute('''CREATE TABLE IF NOT EXISTS flow_queue (
        id TEXT PRIMARY KEY, mode TEXT NOT NULL, phase TEXT NOT NULL,
        credits REAL, created REAL NOT NULL, started REAL, finished REAL,
        recovery_revision INTEGER NOT NULL DEFAULT 0, metrics TEXT NOT NULL DEFAULT '{}')''')
    db.execute('CREATE TABLE IF NOT EXISTS flow_policy (id INTEGER PRIMARY KEY CHECK(id=1), value TEXT NOT NULL)')
    db.commit()
    try:
        with db:
            yield db
    finally:
        db.close()


def policy_in(db):
    row = db.execute('SELECT value FROM flow_policy WHERE id=1').fetchone()
    return {**DEFAULT_POLICY, **(json.loads(row['value']) if row else {})}


def configure(provider, data, changes=None):
    with provider.LOCK, database(provider, data) as db:
        value = policy_in(db)
        if changes is not None:
            if not isinstance(changes, dict) or set(changes) - set(DEFAULT_POLICY):
                raise ValueError('Ajustes Flow no válidos.')
            for key, item in changes.items():
                if key in {'paused','allow_unknown_cost'}:
                    if type(item) is not bool: raise ValueError('Ajuste Flow booleano no válido.')
                elif key in {'max_results','max_total_results','max_pending'}:
                    if type(item) is not int or not 1 <= item <= 120: raise ValueError('Límite Flow entre 1 y 120.')
                elif type(item) not in {int,float} or not math.isfinite(item) or not 0 <= item <= 100000:
                    raise ValueError('Presupuesto Flow no válido.')
            value.update(changes)
            db.execute('INSERT OR REPLACE INTO flow_policy VALUES (1,?)',(json.dumps(value),))
    if changes is not None and not value['paused']:
        kick(provider, data)
    return value


def overview(provider, data):
    with provider.LOCK, database(provider, data) as db:
        policy = policy_in(db)
        rows = db.execute('SELECT * FROM flow_queue ORDER BY created DESC LIMIT 120').fetchall()
        total = db.execute("SELECT COUNT(*),COALESCE(SUM(credits),0) FROM flow_queue WHERE phase IN ('queued','running','uncertain')").fetchone()
    return {'policy':policy, 'pending':total[0], 'reserved_credits':total[1],
            'requests':[{'id':r['id'],'mode':r['mode'],'phase':r['phase'],
                         'created_at':r['created'],'started_at':r['started'],'finished_at':r['finished'],
                         'metrics':json.loads(r['metrics'])} for r in rows]}


def kick(provider, data):
    key = str(Path(data).resolve())
    with START_LOCK:
        if key in RUNNERS:
            return
        runner = threading.Thread(target=consume, args=(provider, data, key), daemon=True,
                                  name='NexusFlowQueue')
        RUNNERS[key] = runner
        try:
            runner.start()
        except Exception:
            RUNNERS.pop(key, None)
            raise RuntimeError('La cola se ha guardado, pero no pudo iniciarse. Reinicia NEXUS; no repitas el envío.') from None


def initialize(provider, data):
    """Adopt old/crashed reservations once. Only result reads may run at startup."""
    key = str(Path(data).resolve())
    with START_LOCK:
        if key in INITIALIZED: return
        with provider.LOCK, database(provider, data) as db:
            rows = db.execute('SELECT jobs.*,flow_queue.phase AS queue_phase,flow_queue.mode AS queue_mode FROM jobs LEFT JOIN flow_queue ON jobs.id=flow_queue.id').fetchall()
            for row in rows:
                result = json.loads(row['result']) if row['result'] else {}
                if row['queue_phase'] in {'queued','running'} or (row['queue_phase'] is None and result.get('status') in {None,'in_progress','needs_reconciliation','failed','error'}):
                    # Pending approvals do not survive a restart as permission to spend.
                    revision = max(int(time.time()*1000), result.get('recovery_started_at',0)+1)
                    phase = 'not_sent' if row['queue_phase']=='queued' and row['queue_mode']=='submit' and result.get('status')=='queued' else 'queued'
                    if phase == 'queued':
                        result.update(status='queued',recovery_started_at=revision)
                    else:
                        result.update(status='not_sent')
                    db.execute('UPDATE jobs SET result=? WHERE id=?',(json.dumps(result),row['id']))
                    db.execute('''INSERT INTO flow_queue(id,mode,phase,created,recovery_revision) VALUES (?,'recover',?,?,?)
                        ON CONFLICT(id) DO UPDATE SET mode='recover',phase=excluded.phase,recovery_revision=excluded.recovery_revision''',
                        (row['id'],phase,time.time(),revision))
        INITIALIZED.add(key)
    kick(provider, data)


def submit(provider, data, args):
    params = provider.parameters(args)
    count = params.get('count',1)
    account_direct = args.get('account_direct') is True
    if account_direct and count > 20: raise ValueError('Puedes pedir hasta 20 resultados por encargo.')
    if count > MAX_RESULTS: raise ValueError('Máximo 120 resultados por solicitud Flow.')
    identifier, prompt = args.get('request_id'), args.get('prompt','')
    if not isinstance(identifier,str) or not provider.IDENTIFIER.fullmatch(identifier) or not isinstance(prompt,str):
        raise ValueError('Identificador o prompt Flow no válido.')
    if 'source_request_id' not in params or params['model']=='video_edit':
        if not prompt.strip(): raise ValueError('Añade un prompt Flow.')
        params['prompt'] = prompt.strip()
    with provider.LOCK, database(provider, data) as db:
        row = db.execute('SELECT params FROM jobs WHERE id=?',(identifier,)).fetchone()
    if row:
        previous = json.loads(row['params']); previous.pop('expected_credits',None)
        if previous != params: raise ValueError('El identificador pertenece a otro trabajo. No se envía de nuevo.')
        return provider.status(data, identifier)
    if any(k in params for k in provider.INPUTS): provider.media_parameters(data,params)
    before = time.monotonic()
    credits = None if account_direct else provider.quote(params,data)['amount']
    if credits is not None and (type(credits) not in {int,float} or not math.isfinite(credits) or credits<0):
        raise ValueError('Precio Flow no válido.')
    if not account_direct and (args.get('cost') != credits or type(args.get('cost')) is bool):
        raise ValueError('Actualiza la estimación Flow antes del envío.')
    if 'source_request_id' in params and params['model']!='video_edit': params['expected_credits']=credits
    with provider.LOCK, database(provider, data) as db:
        db.execute('BEGIN IMMEDIATE')
        row = db.execute('SELECT params FROM jobs WHERE id=?',(identifier,)).fetchone()
        if row:
            if json.loads(row['params'])!=params: raise ValueError('El identificador pertenece a otro trabajo.')
        else:
            policy = policy_in(db)
            if count>policy['max_results']: raise ValueError('El encargo supera el límite de resultados Flow.')
            if credits is None and not account_direct and not policy['allow_unknown_cost']:
                raise ValueError('Coste Flow sin importe. Activa expresamente su autorización en Control Flow.')
            pending = db.execute("SELECT COUNT(*),COALESCE(SUM(credits),0) FROM flow_queue WHERE phase IN ('queued','running','uncertain')").fetchone()
            reserved_results=sum(json.loads(r[0]).get('count',1) for r in db.execute("SELECT jobs.params FROM jobs JOIN flow_queue q ON q.id=jobs.id WHERE q.phase IN ('queued','running','uncertain')"))
            if reserved_results+count>policy['max_total_results']:
                raise ValueError('El encargo supera el límite total de resultados pendientes de Flow.')
            if pending[0]>=policy['max_pending']: raise ValueError('La cola Flow está llena.')
            if credits is not None and pending[1]+credits>policy['max_credits']:
                raise ValueError('El encargo supera el presupuesto de créditos pendientes de Flow.')
            result = {'status':'queued'}
            db.execute('INSERT INTO jobs VALUES (?,?,?)',(identifier,json.dumps(params),json.dumps(result)))
            metrics = {'quote_ms':round((time.monotonic()-before)*1000), 'account_direct':account_direct}
            db.execute("INSERT INTO flow_queue(id,mode,phase,credits,created,metrics) VALUES (?,'submit','queued',?,?,?)",
                       (identifier,credits,time.time(),json.dumps(metrics)))
    kick(provider,data)
    return provider.status(data,identifier)


def recover(provider, data, identifier):
    if not isinstance(identifier,str) or not provider.IDENTIFIER.fullmatch(identifier):
        raise ValueError('Identificador Flow no válido.')
    with provider.LOCK, database(provider,data) as db:
        if not db.execute('SELECT 1 FROM jobs WHERE id=?',(identifier,)).fetchone():
            raise ValueError('Trabajo Flow no encontrado. No se envía ninguna generación.')
    try:
        current = provider.status(data,identifier)
    except (OSError,ValueError,KeyError):
        current = {'id':identifier,'status':'failed','uncertain':True}
    if current['status']=='completed': return current
    with provider.LOCK, database(provider,data) as db:
        existing = db.execute('SELECT phase FROM flow_queue WHERE id=?',(identifier,)).fetchone()
        if existing and existing['phase'] in {'queued','running'}: return current
        row = db.execute('SELECT result FROM jobs WHERE id=?',(identifier,)).fetchone()
        result = json.loads(row['result']) if row['result'] else {}
        revision = max(int(time.time()*1000),result.get('recovery_started_at',0)+1)
        result.update(status='queued',recovery_started_at=revision)
        db.execute('UPDATE jobs SET result=? WHERE id=?',(json.dumps(result),identifier))
        db.execute("""INSERT INTO flow_queue(id,mode,phase,created,recovery_revision) VALUES (?,'recover','queued',?,?)
            ON CONFLICT(id) DO UPDATE SET mode='recover',phase='queued',recovery_revision=excluded.recovery_revision""",
            (identifier,time.time(),revision))
    kick(provider,data)
    return provider.status(data,identifier)


def cancel(provider, data, identifier):
    with provider.LOCK, database(provider,data) as db:
        row = db.execute('SELECT phase,mode FROM flow_queue WHERE id=?',(identifier,)).fetchone()
        if not row or row['phase']!='queued' or row['mode']!='submit':
            raise ValueError('Solo puedes cancelar solicitudes aún no enviadas.')
        db.execute("UPDATE flow_queue SET phase='not_sent',finished=? WHERE id=?",(time.time(),identifier))
        db.execute('UPDATE jobs SET result=? WHERE id=?',(json.dumps({'status':'not_sent'}),identifier))
    return provider.status(data,identifier)


def consume(provider, data, key):
    try:
        while True:
            with provider.LOCK, database(provider,data) as db:
                # Recovery continues while spending is paused.
                row = db.execute("SELECT q.*,j.params FROM flow_queue q JOIN jobs j ON j.id=q.id WHERE q.phase='queued' "
                                 + ("AND q.mode='recover' " if policy_in(db)['paused'] else '')
                                 + "ORDER BY CASE WHEN q.mode='recover' THEN 0 ELSE 1 END,q.created LIMIT 1").fetchone()
            if row is None: break
            try:
                with profile_gate(provider.RUNTIME, timeout=1):
                    with provider.LOCK, database(provider,data) as db:
                        # The row may have been cancelled or paused while waiting.
                        phase = db.execute('SELECT phase FROM flow_queue WHERE id=?',(row['id'],)).fetchone()['phase']
                        if phase!='queued' or row['mode']=='submit' and policy_in(db)['paused']: continue
                        db.execute("UPDATE flow_queue SET phase='running',started=? WHERE id=? AND phase='queued'",(time.time(),row['id']))
                        provider.WORKERS.add(row['id'])
                        params=json.loads(row['params'])
                        previous=db.execute('SELECT result FROM jobs WHERE id=?',(row['id'],)).fetchone()['result']
                        result=json.loads(previous) if previous else {}
                        result.update(status='in_progress',recovery_started_at=row['recovery_revision'])
                        db.execute('UPDATE jobs SET result=? WHERE id=?',(json.dumps(result),row['id']))
                    started=time.monotonic()
                    metrics=json.loads(row['metrics'])
                    metrics['queue_ms']=max(0,round((time.time()-row['created'])*1000))
                    transport_started=False
                    try:
                        if row['mode']=='submit':
                            with provider.LOCK, database(provider,data) as db:
                                policy=policy_in(db)
                                reserved=db.execute("SELECT COALESCE(SUM(credits),0) FROM flow_queue WHERE phase IN ('queued','running','uncertain')").fetchone()[0]
                                reserved_results=sum(json.loads(r[0]).get('count',1) for r in db.execute("SELECT jobs.params FROM jobs JOIN flow_queue q ON q.id=jobs.id WHERE q.phase IN ('queued','running','uncertain')"))
                            direct = metrics.get('account_direct') is True
                            if params.get('count',1)>policy['max_results'] or reserved_results>policy['max_total_results'] or row['credits'] is None and not direct and not policy['allow_unknown_cost'] or reserved>policy['max_credits']:
                                raise ValueError('La autorización de coste o los límites han cambiado.')
                            # Never spend more than the amount reviewed, even after queue delays.
                            latest=None if direct else provider.quote(params,data)['amount']
                            if not direct and latest!=row['credits']:
                                raise ValueError('El coste cambió en la cola; revisa el encargo antes de crear uno nuevo.')
                        transport_started=True
                        provider.work(data,row['id'],params,recover=row['mode']=='recover',
                                      recovery_started_at=row['recovery_revision'])
                    except Exception:
                        with provider.LOCK, database(provider,data) as db:
                            db.execute('UPDATE jobs SET result=? WHERE id=?',
                                       (json.dumps({'status':'needs_reconciliation' if transport_started else 'not_sent',
                                                    'error':'Comprueba el trabajo existente; no repitas el envío.' if transport_started else 'La configuración o el precio requieren revisión.'}),row['id']))
                    finally:
                        provider.WORKERS.discard(row['id'])
                    metrics['recovery_ms' if row['mode']=='recover' else 'execution_ms']=round((time.monotonic()-started)*1000)
                    with provider.LOCK, database(provider,data) as db:
                        proof=json.loads(db.execute('SELECT result FROM jobs WHERE id=?',(row['id'],)).fetchone()['result'] or '{}')
                    if proof.get('status')=='completed':
                        try:
                            verified=provider.status(data,row['id'])
                            metrics['materialize_ms']=verified.get('timings',{}).get('materialize_ms',0)
                            if verified['status']!='completed':raise ValueError('Incomplete result')
                        except (OSError,ValueError,KeyError):
                            proof['status']='needs_reconciliation'
                            with provider.LOCK, database(provider,data) as db:
                                db.execute('UPDATE jobs SET result=? WHERE id=?',(json.dumps(proof),row['id']))
                    with provider.LOCK, database(provider,data) as db:
                        result=json.loads(db.execute('SELECT result FROM jobs WHERE id=?',(row['id'],)).fetchone()['result'] or '{}')
                        phase='completed' if result.get('status')=='completed' else 'not_sent' if result.get('status')=='not_sent' else 'uncertain'
                        metrics['total_ms']=max(0,round((time.time()-row['created'])*1000))
                        db.execute('UPDATE flow_queue SET phase=?,finished=?,metrics=? WHERE id=?',
                                   (phase,time.time(),json.dumps(metrics),row['id']))
                        if phase=='uncertain' and not metrics.get('account_direct'):
                            policy=policy_in(db);policy['paused']=True
                            db.execute('INSERT OR REPLACE INTO flow_policy VALUES(1,?)',(json.dumps(policy),))
            except RuntimeError:
                time.sleep(.5)
    finally:
        with START_LOCK:
            # Close the submit/exit race without busy restarting a paused queue.
            with provider.LOCK, database(provider,data) as db:
                pending=db.execute("SELECT 1 FROM flow_queue WHERE phase='queued' "
                                   + ("AND mode='recover' " if policy_in(db)['paused'] else '') + 'LIMIT 1').fetchone()
            RUNNERS.pop(key,None)
            if pending: kick(provider,data)


def install(provider):
    original_status=provider.status
    original_catalog=provider.catalog
    original_call=provider.call
    def call(tool,args,request_id):
        with profile_gate(provider.RUNTIME):
            return original_call(tool,args,request_id)
    provider.call=call
    def catalog():
        models=original_catalog()
        for model in models:
            for param in model.get('parameters',[]):
                if param['key']=='count':param['maxValue']=MAX_RESULTS
                if param['key']=='duration' and model['providerModelId']=='veo_quality':param['options']=['8']
        return models
    provider.catalog=catalog
    def status(data, identifier):
        started=time.monotonic()
        try:
            response=original_status(data,identifier)
        except (OSError,ValueError,KeyError):
            with provider.LOCK, database(provider,data) as db:
                existing=db.execute('SELECT result FROM jobs WHERE id=?',(identifier,)).fetchone()
            if not existing:raise
            proof=json.loads(existing['result']) if existing['result'] else {}
            running=identifier in provider.WORKERS
            response={'id':identifier,'status':'queued' if proof.get('status')=='queued' else 'in_progress' if running else 'failed',
                      'outputs':[], 'uncertain':not running and proof.get('status')!='queued',
                      'error':'El archivo requiere recuperar el resultado existente. No se ha reenviado.',
                      'recovery_started_at':proof.get('recovery_started_at',0)}
        with provider.LOCK, database(provider,data) as db:
            row=db.execute('SELECT params,result FROM jobs WHERE id=?',(identifier,)).fetchone()
            queue=db.execute('SELECT * FROM flow_queue WHERE id=?',(identifier,)).fetchone()
        params=json.loads(row['params']);result=json.loads(row['result']) if row['result'] else {}
        response['requested_model']=result.get('requested_model',params.get('model'))
        response['model_confirmed']=result.get('model_confirmed') is True
        if queue:
            response['queue_phase']=queue['phase'];response['timings']=json.loads(queue['metrics'])
            if response['outputs']:
                response['timings']['materialize_ms']=round((time.monotonic()-started)*1000)
                with provider.LOCK, database(provider,data) as db:
                    db.execute('UPDATE flow_queue SET metrics=? WHERE id=?',(json.dumps(response['timings']),identifier))
            response['reconciling']=queue['mode']=='recover' and queue['phase'] in {'queued','running'}
        if response['status']=='not_sent':
            response.update(uncertain=False,error=result.get('error','No enviado. Crea otro encargo si quieres enviarlo.'))
        return response
    provider.status=status
    provider.submit=lambda data,args:submit(provider,data,args)
    provider.recover=lambda data,identifier:recover(provider,data,identifier)
    provider.automation_initialize=lambda data:initialize(provider,data)
    provider.automation_overview=lambda data:overview(provider,data)
    provider.automation_configure=lambda data,args:configure(provider,data,args)
    provider.automation_cancel=lambda data,identifier:cancel(provider,data,identifier)
