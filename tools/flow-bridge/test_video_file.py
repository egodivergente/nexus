import struct
import unittest
from video_file import inspect

def box(kind,data):return struct.pack('>I4s',8+len(data),kind)+data

class ContainerTests(unittest.TestCase):
    def test_duration_and_dimensions_are_read_from_headers(self):
        mvhd=b'\0'*12+struct.pack('>II',1000,8000)
        tkhd=b'\0'*72+struct.pack('>II',1280<<16,720<<16)
        data=box(b'ftyp',b'isom')+box(b'mdat',b'fixture')+box(b'moov',box(b'mvhd',mvhd)+box(b'trak',box(b'tkhd',tkhd)))
        result=inspect(data)
        self.assertEqual(result['duration_seconds'],8)
        self.assertEqual(result['dimensions'],[1280,720])
    def test_truncated_and_nonvideo_data_are_rejected(self):
        for data in (b'<html>login</html>',struct.pack('>I4s',999,b'ftyp')+b'isom',box(b'ftyp',b'isom')):
            with self.assertRaises(ValueError):inspect(data)
