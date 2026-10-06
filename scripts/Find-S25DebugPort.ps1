Add-Type -TypeDefinition @"
using System;
using System.Net.Sockets;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
public class NexusAdbDiscovery {
 public static async Task<int[]> Scan() {
  var ports=new List<int>(); var tasks=new List<Task>(); var gate=new SemaphoreSlim(48);
  for(int p=32768;p<=60999;p++) { await gate.WaitAsync(); int port=p;
   tasks.Add(Task.Run(async()=>{using(var c=new TcpClient()){ try {var t=c.ConnectAsync("100.119.88.105",port); if(await Task.WhenAny(t,Task.Delay(150))==t && c.Connected) lock(ports) ports.Add(port);} catch {} finally{gate.Release();}}}));
  }
  await Task.WhenAll(tasks); return ports.ToArray();
 }
}
"@
[NexusAdbDiscovery]::Scan().GetAwaiter().GetResult() | Tee-Object outputs\s25-open-debug-ports.txt
