package com.zx.bt.socket.processor;

import com.zx.bt.config.Config;
import com.zx.bt.dto.MessageInfo;
import com.zx.bt.entity.Node;
import com.zx.bt.enums.MethodEnum;
import com.zx.bt.enums.NodeRankEnum;
import com.zx.bt.enums.YEnum;
import com.zx.bt.store.RoutingTable;
import com.zx.bt.task.GetPeersTask;
import com.zx.bt.util.BTUtil;
import com.zx.bt.util.CodeUtil;
import com.zx.bt.util.SendUtil;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

/**
 * author:ZhengXing
 * datetime:2018/3/1 0001 10:30
 * ANNOUNCE_PEER 请求 处理器
 */
@Slf4j
@Component
public class GetPeersRequestUDPProcessor extends UDPProcessor{
	private static final String LOG = "[GET_PEERS]";

	private final List<RoutingTable> routingTables;
	private final GetPeersTask getPeersTask;

	public GetPeersRequestUDPProcessor(List<RoutingTable>  routingTables, GetPeersTask getPeersTask) {
		this.routingTables = routingTables;
		this.getPeersTask = getPeersTask;
	}

	@Override
	boolean process1(ProcessObject processObject) {
		Map<String, Object> rawMap = processObject.getRawMap();
		MessageInfo messageInfo = processObject.getMessageInfo();
		InetSocketAddress sender = processObject.getSender();
		int index = processObject.getIndex();

		Map<String, Object> aMap = BTUtil.getParamMap(rawMap, "a", "GET_PEERS,找不到a参数.map:" + rawMap);
		String info_hash = CodeUtil.bytes2HexStr(BTUtil.getParamString(aMap, "info_hash", "GET_PEERS,找不到info_hash参数.map:" + rawMap).getBytes(CharsetUtil.ISO_8859_1));
		byte[] id = BTUtil.getParamString(aMap, "id", "GET_PEERS,找不到id参数.map:" + rawMap).getBytes(CharsetUtil.ISO_8859_1);
		List<Node> nodes = routingTables.get(index).getForTop8(CodeUtil.hexStr2Bytes(info_hash));
//                    log.info("{}GET_PEERS,发送者:{},info_hash:{}", LOG, sender,info_hash);
		//回复时,将自己的nodeId伪造为 和该节点异或值相差不大的值
		SendUtil.getPeersReceive(messageInfo.getMessageId(), sender,
				CodeUtil.generateSimilarInfoHashString(info_hash, config.getMain().getSimilarNodeIdNum()),
				config.getMain().getToken(), nodes, index);
		//加入路由表
		routingTables.get(index).put(new Node(id, BTUtil.getIpBySender(sender), sender.getPort(), NodeRankEnum.GET_PEERS.getCode()));
		//开始查找任务
		getPeersTask.put(info_hash, index);
		return true;
	}

	@Override
	boolean isProcess(ProcessObject processObject) {
		return MethodEnum.GET_PEERS.equals(processObject.getMessageInfo().getMethod()) && YEnum.QUERY.equals(processObject.getMessageInfo().getStatus());
	}
}