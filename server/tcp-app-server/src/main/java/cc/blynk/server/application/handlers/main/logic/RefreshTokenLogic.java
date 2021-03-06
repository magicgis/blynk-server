package cc.blynk.server.application.handlers.main.logic;

import cc.blynk.server.core.dao.TokenManager;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.utils.ParseUtil;
import io.netty.channel.ChannelHandlerContext;

import static cc.blynk.server.core.protocol.enums.Command.REFRESH_TOKEN;
import static cc.blynk.utils.ByteBufUtil.makeStringMessage;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public class RefreshTokenLogic {

    private final TokenManager tokenManager;

    public RefreshTokenLogic(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    public void messageReceived(ChannelHandlerContext ctx, User user, StringMessage message) {
        String dashBoardIdString = message.body;

        int dashId = ParseUtil.parseInt(dashBoardIdString);

        user.profile.validateDashId(dashId);

        String token = tokenManager.refreshToken(user, dashId);

        ctx.writeAndFlush(makeStringMessage(REFRESH_TOKEN, message.id, token), ctx.voidPromise());
    }
}
