package com.umang.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.umang.notification.channel.ChannelFactory;
import com.umang.notification.channel.EmailSender;
import com.umang.notification.channel.NotificationChannel;
import com.umang.notification.channel.PushSender;
import com.umang.notification.channel.SmsSender;
import com.umang.notification.model.enums.Channel;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Strategy/Factory selects the right sender bean by channel enum. */
class ChannelFactoryTest {

    private final EmailSender email = new EmailSender(0.0);
    private final SmsSender sms = new SmsSender(0.0);
    private final PushSender push = new PushSender(0.0);
    private final ChannelFactory factory = new ChannelFactory(List.of(email, sms, push));

    @Test
    void selectsSenderMatchingRequestedChannel() {
        assertThat(factory.forChannel(Channel.EMAIL)).isSameAs(email);
        assertThat(factory.forChannel(Channel.SMS)).isSameAs(sms);
        assertThat(factory.forChannel(Channel.PUSH)).isSameAs(push);
    }

    @Test
    void eachSenderReportsItsOwnChannel() {
        assertThat(email.channel()).isEqualTo(Channel.EMAIL);
        assertThat(sms.channel()).isEqualTo(Channel.SMS);
        assertThat(push.channel()).isEqualTo(Channel.PUSH);
    }

    @Test
    void throwsWhenNoSenderRegistered() {
        // A factory with only EMAIL registered cannot serve SMS.
        ChannelFactory partial = new ChannelFactory(List.<NotificationChannel>of(email));
        assertThatThrownBy(() -> partial.forChannel(Channel.SMS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
