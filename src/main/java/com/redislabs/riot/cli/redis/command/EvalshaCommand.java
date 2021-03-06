package com.redislabs.riot.cli.redis.command;

import com.redislabs.riot.redis.writer.map.Evalsha;

import io.lettuce.core.ScriptOutputType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "evalsha", description = "Evaluates a script cached on the server side by its SHA1 digest")
public class EvalshaCommand extends AbstractRedisCommand {

	@Option(names = "--args", arity = "1..*", description = "Arg field names", paramLabel = "<names>")
	private String[] args = new String[0];
	@Option(names = "--keys", arity = "1..*", description = "Key field names", paramLabel = "<names>")
	private String[] keys = new String[0];
	@Option(names = "--sha", description = "SHA1 digest", paramLabel = "<sha>")
	private String sha;
	@Option(names = "--output", description = "Output: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})", paramLabel = "<type>")
	private ScriptOutputType outputType = ScriptOutputType.STATUS;

	@Override
	public Evalsha redisWriter() {
		Evalsha writer = new Evalsha();
		writer.args(args);
		writer.keys(keys);
		writer.outputType(outputType);
		writer.sha(sha);
		return writer;
	}

}
