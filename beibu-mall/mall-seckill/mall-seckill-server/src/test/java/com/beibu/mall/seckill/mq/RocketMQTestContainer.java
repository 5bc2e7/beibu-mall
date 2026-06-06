package com.beibu.mall.seckill.mq;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.awaitility.Awaitility;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * RocketMQ Testcontainers 辅助类
 *
 * 单容器模式：NameServer + Broker 在一个容器中
 * 参考 Apache Brave 和 Debezium 的实现
 */
public class RocketMQTestContainer extends GenericContainer<RocketMQTestContainer> {

    private static final String IMAGE = "apache/rocketmq";
    private static final String VERSION = "5.3.1";
    public static final int NAMESRV_PORT = 9876;
    public static final int BROKER_PORT = 10911;

    public RocketMQTestContainer() {
        super(DockerImageName.parse(IMAGE + ":" + VERSION));
        withExposedPorts(NAMESRV_PORT, BROKER_PORT);
        // CI 环境内存限制
        withEnv("JAVA_OPT_EXT", "-Xms256m -Xmx256m");
    }

    @Override
    protected void configure() {
        // 启动 NameServer 和 Broker
        String command = "#!/bin/bash\n" +
                "sh mqnamesrv &\n" +
                "sleep 5\n" +
                "sh mqbroker -n localhost:" + NAMESRV_PORT;
        withCommand("sh", "-c", command);

        // 等待 NameServer 启动成功
        this.waitStrategy = Wait.forLogMessage(".*boot success.*", 1)
                .withStartupTimeout(Duration.ofSeconds(90));
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        // 关键：更新 brokerIP1 配置，让客户端能从外部连接
        // brokerIP1 必须是主机地址，客户端会用这个地址连接 broker
        List<String> commands = new ArrayList<>();
        commands.add(updateBrokerConfig("brokerIP1", getHost()));
        commands.add(updateBrokerConfig("listenPort", getMappedPort(BROKER_PORT)));
        commands.add(updateBrokerConfig("autoCreateTopicEnable", true));
        commands.add(updateBrokerConfig("brokerPermission", 6));

        String command = String.join(" && ", commands);
        try {
            ExecResult result = execInContainer("/bin/sh", "-c", command);
            if (result != null && result.getExitCode() != 0) {
                throw new IllegalStateException("Failed to update broker config: " + result);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update broker config", e);
        }

        // 等待 Broker 就绪
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String updateBrokerConfig(String key, Object value) {
        return String.format(
                "./mqadmin updateBrokerConfig -b localhost:%s -k %s -v %s",
                BROKER_PORT, key, value
        );
    }

    /**
     * 获取 NameServer 地址
     */
    public String getNamesrvAddr() {
        return getHost() + ":" + getMappedPort(NAMESRV_PORT);
    }

    /**
     * 创建 Topic（带重试）
     */
    public void createTopic(String topic) {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollDelay(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    ExecResult result = execInContainer(
                            "sh", "mqadmin", "updateTopic",
                            "-n", "localhost:" + NAMESRV_PORT,
                            "-t", topic,
                            "-c", "DefaultCluster"
                    );
                    return result.getExitCode() == 0
                            && result.getStdout() != null
                            && result.getStdout().contains("success");
                });
    }
}
