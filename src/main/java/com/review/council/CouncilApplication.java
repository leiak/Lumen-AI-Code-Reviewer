package com.review.council;

import com.review.council.cli.ReviewCli;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import picocli.CommandLine;

@SpringBootApplication
public class CouncilApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CouncilApplication.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        var ctx = app.run(args);
        try {
            var cli = ctx.getBean(ReviewCli.class);
            // Use Spring context to resolve @Component subcommands
            CommandLine.IFactory factory = new SpringCommandFactory(ctx);
            int code = new CommandLine(cli, factory).execute(args);
            System.exit(code);
        } finally {
            ctx.close();
        }
    }

    /** Resolves picocli subcommand instances from the Spring context. */
    static class SpringCommandFactory implements CommandLine.IFactory {
        private final ApplicationContext ctx;
        SpringCommandFactory(ApplicationContext ctx) { this.ctx = ctx; }
        @Override public <K> K create(Class<K> cls) throws Exception {
            try { return ctx.getBean(cls); }
            catch (Exception e) { return cls.getDeclaredConstructor().newInstance(); }
        }
    }
}
