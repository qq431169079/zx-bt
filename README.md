### BT 

- 尝试一个磁力搜索系统，基于ELK进行存储搜索。
- [BitTorrent官网](http://bittorrent.org)
- [Bencode编解码Github项目](https://github.com/dampcake/bencode)-废弃(自行实现了)
- [官网文档翻译-博客1](http://www.cnblogs.com/bymax/p/4973639.html)
- [官网文档翻译-博客2](https://blog.sharpbai.com/2014/05/bittorrent-dht%E5%8D%8F%E8%AE%AE%E4%B8%AD%E6%96%87%E7%BF%BB%E8%AF%91/)

#### 奇淫巧技
- ISO_8859_1 编码可表示0x00 - 0xff 范围(单字节)的所有字符.而不会发生UTF-8/ASCII等编码中的无法识别字符.导致byte[]转为String后,再转回byte[]时
发生变化.

- linux后台运行nohup.不产生.out文件的命令(运行了没一会,1G+的文件我去)
> nohup java -jar /xxx/xxx/xxx.jar >/dev/null 2>&1 &

#### bug
- 需要确保nodeId不与其他人重复,否则可能无法得到其他节点的响应(应该是因为,其他节点进行回复时会事先查询他们自己已有的路由表,如果有存在的节点,就.....)
- 自己手贱,在netty的handle的异常捕获方法中,当发生异常就关闭该udp连接,导致各种bug...

#### 注意点
- peer的联系信息编码为6字节长的字符串，也称作”Compact IP-address/port info”。其中前4个字节是网络字节序（大端序(高字节存于内存低地址，低字节存于内存高地址)）的IP地址，后2个字节是网络字节序的端口号。
  
- node的联系信息编码为26字节长的字符串，也称作”Compact node info”。其中前20字节是网络字节序的node ID，后面6个字节是peer的”Compact IP-address/port info”。

- byte[]转int等. 是将byte[0] 左位移最高位数,例如将2个byte转为int,是( bytes[1] & 0xFF) | (bytes[0] & 0xFF) << 8 而不是 ( bytes[0] & 0xFF) | (bytes[1] & 0xFF) << 8.  
其原因很简单,按照从左到右的四位,2个byte 00011100, 11100011. 显然是要变为 0001110011100011,将第一个byte放到第二个byte前面,那么也就是让第一个byte左位移8位即可

 
#### 简述
- BitTorrent是分发文件的协议。它通过URL标识内容，旨在与网络无缝集成。
它优于简单的HTTP的优点是，当同时发生同一文件的多个下载时，下载器会相互上传，从而使文件源可以支持非常大量的下载程序，并且只有轻微的负载增加。

- 节点(node):只要实现它的DHT(Distributed Hash Table)协议就可以将自己作为一个节点注册到该P2P网络中去.每个节点的大致功能是(此处暂不考虑下载功能):
    - 可发送find_node请求,向其他节点(第一次查找时通常为该P2P网络上著名的节点)查找指定ID(SHA1压缩,160bit组成的随机值)的节点(目标节点).
        - 如果对方节点保存了目标node信息,即返回你该node的id/ip/port等.
        - 如果没有,它则返回你,和目标节点ID最相似(根据ID进行异或计算出相似度)的Top8节点的信息,然后你需要再去访问这些节点,继续发送find_node请求查找目标节点.
        - 当然,他也可以返回你空,表示他还没有存储任何节点
        - 我们可将查找到的每个节点保存到自己的存储中(routing table)
        - 通常,对方也会将我们保存到他们的路由表中
    - 当自己收到其他节点发送的find_node请求时,可按照常规流程查找出最相似的Top8个节点返回.不过也可以无论如何都将自己伪装成其中一个相似节点返回回去,以让自己更容易被别人找到.
        - 或者 返回一个空,表示自己没有存储任何节点
    - 可使用get_peers请求,根据资源ID查找某个资源,返回信息中包含有对应资源的ip:port. 
    - 当收到别人的get_peers请求时,请求中的infohash就是我们需要的种子.
    - ping: 检测某个节点状态,已更新自己的路由表
    - 当某个节点有了某个资源时,会向之前向它请求过的所有节点,发送通知(应该就是在他自己的路由表中广播消息),BT嗅探器也正是基于这点进行操作.
    
- 综上所述,我们只需要从一个目标节点开始,不停地查找其他其他节点,并当其他节点请求我们时,让回复的nodes中包含自己,以扩大社交面积, 然后等待我们认识的nodes通知我们它拥有哪些资源,从而我们向它请求这些资源即可.


#### 根据info_hash获取到种子的metadata
- 获取到info_hash后一直找不到获取种子信息的方法,例如该种子中包含的资源列表,种子名等.
- 搜索了下,大多数的方法是从已存在的种子库中搜索该中子的信息.这个很简单,例如百度云,可以解析你输入的磁力链接,那么只要将info_hash转为磁力链接(加个前缀即可),
然后请求百度云的那个链接即可.(此处只是举例,百度云的该接口可能需要权限,也应该会做调用次数限制.)
- 官网语焉不详,网上的博客也完全找不到对应的详细协议介绍.StackOverFlow也搜索遍了,一无所获.
- 最后实在没法子,找了github上很多的实现DHT协议的项目看源码,java的类似项目似乎不多,go和py倒是一堆.  
最后在一个go项目中找到了对应协议的实现:通过TCP连接向peer发送请求报文,获取下载(2018年2月17日 01:13:23....这几晚都熬夜,,都在搞这个项目..)
