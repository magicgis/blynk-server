package cc.blynk.server.application.handlers.main.logic.reporting;

import cc.blynk.server.core.BlockingIOProcessor;
import cc.blynk.server.core.dao.ReportingDao;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.enums.PinType;
import cc.blynk.server.core.protocol.exceptions.IllegalCommandBodyException;
import cc.blynk.server.core.protocol.exceptions.IllegalCommandException;
import cc.blynk.server.core.protocol.exceptions.NoDataException;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.core.reporting.GraphPinRequest;
import cc.blynk.utils.ParseUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

import static cc.blynk.server.core.protocol.enums.Command.GET_GRAPH_DATA_RESPONSE;
import static cc.blynk.server.core.protocol.enums.Response.NO_DATA;
import static cc.blynk.server.core.protocol.enums.Response.SERVER_ERROR;
import static cc.blynk.utils.ByteBufUtil.makeBinaryMessage;
import static cc.blynk.utils.ByteBufUtil.makeResponse;
import static cc.blynk.utils.ByteBufUtil.ok;
import static cc.blynk.utils.ByteUtils.compress;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public class GetGraphDataLogic {

    private static final Logger log = LogManager.getLogger(GetGraphDataLogic.class);

    private final BlockingIOProcessor blockingIOProcessor;
    private final ReportingDao reportingDao;

    public GetGraphDataLogic(ReportingDao reportingDao, BlockingIOProcessor blockingIOProcessor) {
        this.reportingDao = reportingDao;
        this.blockingIOProcessor = blockingIOProcessor;
    }

    public void messageReceived(ChannelHandlerContext ctx, User user, StringMessage message) {
        //warn: split may be optimized
        //todo remove space after app migration
        String[] messageParts = message.body.split(" |\0");

        if (messageParts.length < 3) {
            throw new IllegalCommandException("Wrong income message format.");
        }

        //special case for delete command
        if (messageParts.length == 4) {
            deleteGraphData(messageParts, user.name);
            ctx.writeAndFlush(ok(message.id), ctx.voidPromise());
        } else {
            int dashId = ParseUtil.parseInt(messageParts[0]);
            user.profile.validateDashId(dashId);
            process(ctx.channel(), dashId, Arrays.copyOfRange(messageParts, 1, messageParts.length), user, message.id, 4);
        }
    }

    private void process(Channel channel, int dashId, String[] messageParts, User user, int msgId, int valuesPerPin) {
        int numberOfPins = messageParts.length / valuesPerPin;

        GraphPinRequest[] requestedPins = new GraphPinRequestData[numberOfPins];

        for (int i = 0; i < numberOfPins; i++) {
            requestedPins[i] = new GraphPinRequestData(dashId, messageParts, i, valuesPerPin);
        }

        readGraphData(channel, user.name, requestedPins, msgId);
    }

    private void readGraphData(Channel channel, String username, GraphPinRequest[] requestedPins, int msgId) {
        blockingIOProcessor.execute(() -> {
            try {
                byte[][] data = reportingDao.getAllFromDisk(username, requestedPins);
                byte[] compressed = compress(requestedPins[0].dashId, data);

                channel.writeAndFlush(makeBinaryMessage(GET_GRAPH_DATA_RESPONSE, msgId, compressed), channel.voidPromise());
            } catch (NoDataException noDataException) {
                channel.writeAndFlush(makeResponse(msgId, NO_DATA), channel.voidPromise());
            } catch (Exception e) {
                log.error("Error reading reporting data. For user {}", username);
                channel.writeAndFlush(makeResponse(msgId, SERVER_ERROR), channel.voidPromise());
            }
        });
    }

    private void deleteGraphData(String[] messageParts, String username) {
        try {
            int dashBoardId = Integer.parseInt(messageParts[0]);
            PinType pinType = PinType.getPinType(messageParts[1].charAt(0));
            byte pin = Byte.parseByte(messageParts[2]);
            String cmd = messageParts[3];
            if (!"del".equals(cmd)) {
                throw new IllegalCommandBodyException("Wrong body format. Expecting 'del'.");
            }
            reportingDao.delete(username, dashBoardId, pinType, pin);
        } catch (NumberFormatException e) {
            throw new IllegalCommandException("HardwareLogic command body incorrect.");
        }
    }

}
