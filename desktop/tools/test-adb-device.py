"""Selección del S25 por modelo: sin IP fija, con varios dispositivos y con el emparejado inalámbrico duplicado."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import adb_device

SAMPLE = """List of devices attached
192.168.1.134:5555     device product:pa3qxxx model:SM_S938B device:pa3q transport_id:1
adb-R5CXC3J630Y-qhOvma._adb-tls-connect._tcp device product:pa3qxxx model:SM_S938B device:pa3q transport_id:2
emulator-5554          device product:sdk model:sdk_gphone device:emu transport_id:3
100.119.88.105:5555    offline product:pa3qxxx model:SM_S938B device:pa3q transport_id:4

"""
serials = adb_device.parse_devices(SAMPLE)
assert serials == ['192.168.1.134:5555', 'adb-R5CXC3J630Y-qhOvma._adb-tls-connect._tcp'], serials
assert adb_device.parse_devices('List of devices attached\n\n') == []
assert adb_device.parse_devices(SAMPLE, 'SM_X') == []
print('OK adb_device')
