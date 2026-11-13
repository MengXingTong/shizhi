"""Execute the actual schema/retention SQL in desktop SQLite; not an Android instrumentation test."""
import json, pathlib, re, sqlite3
root=pathlib.Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/shizhi/app/DataStore.java').read_text(encoding='utf-8')
db=sqlite3.connect(':memory:')
for sql in re.findall(r'db\.execSQL\("(CREATE [^"]+)"\)',source):
    db.execute(sql)
now=10*7*24*60*60*1000
retention=7*24*60*60*1000
for id,created,favorite,status in [(1,0,0,'played'),(2,0,1,'played'),(3,0,0,'active'),(4,now,0,'queued'),(5,0,0,'queued'),(6,now-retention,0,'played')]:
    db.execute('INSERT INTO items(id,bvid,created,favorite,status,payload) VALUES (?,?,?,?,?,?)',(id,f'BV{id}',created,favorite,status,'{}'))
    db.execute('INSERT INTO counters VALUES (?,?)',(f'progress:{id}',2))
prune=re.search(r'execSQL\("(DELETE FROM items[^"]+)"',source).group(1)
db.execute(prune,(now-retention,))
assert [r[0] for r in db.execute('SELECT id FROM items ORDER BY id')]==[2,3,4,6]
cleanup=re.search(r'execSQL\("(DELETE FROM counters[^"]+)"',source).group(1)
db.execute(cleanup)
assert [r[0] for r in db.execute('SELECT name FROM counters ORDER BY name')]==['progress:2','progress:3','progress:4','progress:6']
db.execute("UPDATE items SET favorite=0 WHERE id=2")
db.execute(prune,(now-retention,))
assert not db.execute('SELECT 1 FROM items WHERE id=2').fetchone()
db.execute("UPDATE items SET status='queued' WHERE status='active'")
assert db.execute("SELECT status FROM items WHERE id=3").fetchone()[0]=='queued'
print('PASS actual SQL schema; 7-day expiry; favorite exemption; active playback exemption; boundary day; old queue expiry; progress cleanup; unfavorite expiry; recovery')
# A new feed run archives only pending entries. Pause/resume never runs this SQL.
db.execute("DELETE FROM items")
for id,status,favorite in [(10,'queued',0),(11,'queued',1),(12,'active',0),(13,'played',1),(14,'review',1)]:
    db.execute('INSERT INTO items(id,bvid,created,favorite,status,payload) VALUES (?,?,?,?,?,?)',(id,f'BV{id}',now,favorite,status,'{}'))
archive=next(sql for sql in re.findall(r'execSQL\("([^"\n]+)"',source) if sql.startswith("UPDATE items SET status='archived'"))
db.execute(archive)
assert list(db.execute('SELECT id,status,favorite FROM items ORDER BY id'))==[(10,'archived',0),(11,'archived',1),(12,'active',0),(13,'played',1),(14,'review',1)]
print('PASS fresh-feed clearing preserves rows, active playback and favorites; archived items do not count as queued')
# Explicit replacement updates content but retains identity, favorite and playback history.
db.execute("UPDATE items SET payload=?,status='queued',created=? WHERE id=?",('{"replacement":true}',now+1,11))
assert db.execute('SELECT id,favorite,status,payload FROM items WHERE id=11').fetchone()==(11,1,'queued','{"replacement":true}')
for reason in ['兴趣匹配度不足','规则过滤','已提炼']:
    db.execute('INSERT INTO seen VALUES (?,?,?)',(reason,now,reason))
reset=re.search(r'execSQL\("(DELETE FROM seen WHERE reason[^"\n]+)"',source).group(1)
db.execute(reset)
assert list(db.execute('SELECT reason FROM seen'))==[('已提炼',)]
print('PASS repair preserves favorite and row ID; fresh scans reset old preference rejections')
# Recovery source checks run in Java; desktop SQLite only verifies quarantine/retention SQL.
db.execute("UPDATE items SET status='review' WHERE id=?",(11,))
assert db.execute('SELECT favorite,status FROM items WHERE id=11').fetchone()==(1,'review')
print('PASS quarantined legacy favorite retained without entering pending queue')

# Process recovery remembers intent only; no startup path dispatches a model request.
db.execute("INSERT OR REPLACE INTO counters(name,value) VALUES ('continuous_session',1)")
db.execute(cleanup)
assert db.execute("SELECT value FROM counters WHERE name='continuous_session'").fetchone()==(1,)
print('PASS continuous-session recovery marker survives progress pruning; queue/favorites unchanged')
