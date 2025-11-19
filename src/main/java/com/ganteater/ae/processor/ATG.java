package com.ganteater.ae.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.ganteater.ae.AELogRecord;
import com.ganteater.ae.processor.annotation.CommandExamples;
import com.ganteater.ae.util.xml.easyparser.Node;

public class ATG extends BaseProcessor {

	private static final String FILE_PREFIX = " File ";
	private static final String DYN_ADMIN_ROOT = "/dyn/admin/nucleus/";
	private static final String DEFAULT_PROTOCOL = "http://";

	@CommandExamples({
			"<RQL name='type:property' host='' repository='' item-descriptor='' query='' username='' password='' />" })
	public void runCommandRQL(Node action) throws IOException {
		String name = attr(action, "name");
		String itemDescriptor = attr(action, "item-descriptor");
		String query = attr(action, "query");
		query = query.replace('\'', '\"');
		boolean idOnly = "true".equalsIgnoreCase(attr(action, "id-only"));

		query = "<query-items item-descriptor=\"" + itemDescriptor + "\" id-only=\"" + idOnly + "\">" + query
				+ "</query-items>";

		String repository = attr(action, "repository");
		if (!StringUtils.endsWith(repository, "/")) {
			repository += "/";
		}

		query = repository + "?xmltext=" + query;

		Connection connect = getConnect(action, query);

		Document doc = connect.get();
		String value = doc.select("h2 ~ pre code").text();
		value = StringUtils.substringAfter(value, "\n");

		List<String> result = new ArrayList<>();
		if (StringUtils.isNotBlank(value)) {
			if (idOnly) {
				result = Arrays.asList(StringUtils.splitByWholeSeparator(value, ", "));
			} else {
				result = Arrays.asList(StringUtils.splitByWholeSeparator(value, "\n\n"));
			}
		}

		setVariableValue(name, result);
	}

	@CommandExamples("<AddItem name='type:property' host='' username='' password='' repository='' item-descriptor='' id='' body='type:property' />")
	public void runCommandAddItem(Node action) throws IOException {

		String repository = attr(action, "repository");
		if (!StringUtils.endsWith(repository, "/")) {
			repository += "/";
		}

		Connection connect = getConnect(action, repository);

		String itemDescriptor = attr(action, "item-descriptor");
		String id = attr(action, "id");
		StringBuilder query = new StringBuilder("<add-item item-descriptor=\"" + itemDescriptor + "\"");
		if (StringUtils.isNotBlank(id)) {
			query.append(" id=\"" + id + "\"");
		}
		query.append(">" + attrValue(action, "body") + "</add-item>");
		executeXtmlText(action, connect, query);
	}

	@CommandExamples("<PrintItem name='type:property' host='' username='' password='' repository='' item-descriptor='' id='' body='type:property' />")
	public void runCommandPrintItem(Node action) throws IOException {

		String repository = attr(action, "repository");
		if (!StringUtils.endsWith(repository, "/")) {
			repository += "/";
		}
		Connection connect = getConnect(action, repository);

		String itemDescriptor = attr(action, "item-descriptor");
		String id = attr(action, "id");
		StringBuilder query = new StringBuilder("<print-item item-descriptor=\"" + itemDescriptor + "\"");
		query.append(" id=\"" + id + "\"");
		query.append("/>");

		String value = getResult(connect, query);

		String name = attr(action, "name");

		setVariableValue(name, StringUtils.substringAfter(value, "\n"));
	}

	@CommandExamples("<DeleteItem name='type:property' host='' username='' password='' repository='' item-descriptor='' id='' />")
	public void runCommandDeleteItem(Node action) throws IOException {

		String repository = attr(action, "repository");
		if (!StringUtils.endsWith(repository, "/")) {
			repository += "/";
		}

		Connection connect = getConnect(action, repository);

		String itemDescriptor = attr(action, "item-descriptor");
		String id = attr(action, "id");
		StringBuilder query = new StringBuilder(
				"<remove-item item-descriptor=\"" + itemDescriptor + "\" id=\"" + id + "\"/>");
		executeXtmlText(action, connect, query);
	}

	@CommandExamples("<GetDescriptors name='type:property' host='' username='' password='' repository='' body='type:property' />")
	public void runCommandGetDescriptors(Node action) throws IOException {
		String name = attr(action, "name");

		String repository = attr(action, "repository");
		if (!StringUtils.endsWith(repository, "/")) {
			repository += "/";
		}

		Connection connect = getConnect(action, repository);
		Document doc = connect.get();
		Elements thElements = doc.select("table:first-of-type th:eq(0)");
		List<String> repoItems = thElements.stream().skip(1).map(e -> e.text()).collect(Collectors.toList());

		setVariableValue(name, repoItems);
	}

	private void executeXtmlText(Node action, Connection connect, StringBuilder query) throws IOException {
		String value = getResult(connect, query);

		String name = attr(action, "name");
		setVariableValue(name, StringUtils.substringAfter(value, ":"));
	}

	private String getResult(Connection connect, StringBuilder query) throws IOException {
		String xmltext = query.toString();
		debug(new AELogRecord(xmltext, "xml", "xmltext"));

		Document doc = connect.data("xmltext", xmltext).post();
		debug(new AELogRecord(doc.html(), "html", "html"));

		String value = doc.select("h2 ~ h2 ~ pre code").text();
		return value;
	}

	private Connection getConnect(Node action, String query) {
		String host = attr(action, "host");
		String port = attr(action, "port");

		String url = DEFAULT_PROTOCOL + host + ":" + port + DYN_ADMIN_ROOT + StringUtils.defaultIfEmpty(query, "");

		debug(new AELogRecord(url, "url", "url"));
		Connection connect = Jsoup.connect(url);

		int timeout = (int) parseTime(action, "timeout", "5000");
		connect.timeout(timeout);

		String username = attr(action, "username");
		if (username != null) {
			String password = attr(action, "password");
			String login = username + ":" + password;
			String base64login = new String(Base64.encodeBase64(login.getBytes()));
			connect.header("Authorization", "Basic " + base64login);
		}
		return connect;
	}

	@CommandExamples({ "<PropertyParser name='type:property' property='' source='' />" })
	public void runCommandPropertyParser(Node action) {
		String name = attr(action, "name");
		String property = attr(action, "property");
		Object source = getVariableValue(attr(action, "source"));

		Object result = null;
		if (source instanceof String) {
			Document parse = Jsoup.parse((String) source);
			result = parse.select("set-property[name=" + property + "]").text();
		} else if (source instanceof List) {
			List<String> array = (List) source;
			result = new ArrayList();
			int i = 0;
			for (String value : array) {
				Document parse = Jsoup.parse(value);
				((List) result).add(parse.select("set-property[name=" + property + "]").text());
			}
		}

		setVariableValue(name, result);
	}

	@CommandExamples({
			"<GetProperty name='type:property' property='' source='' host='' port='' component='' username='' password='' />" })
	public void runCommandGetProperty(Node action) throws IOException {
		String name = attr(action, "name");
		String property = attr(action, "property");
		if (property == null) {
			property = name;
		}

		String query = attr(action, "component") + "?propertyName=" + property;

		Connection connect = getConnect(action, query);

		Document doc = connect.get();
		debug(new AELogRecord("Response: " + doc.toString(), "html", name));
		String text = doc.text();
		String value = StringUtils.substringBetween(text, "Value", "New value");
		if (value != null) {
			if (StringUtils.equals(value.trim(), "null")) {
				value = "null";
			} else if (StringUtils.startsWith(value, FILE_PREFIX)) {
				value = StringUtils.substringAfter(value, FILE_PREFIX).trim();
			} else {
				value = null;
			}
		}

		if (value == null) {
			value = doc.select("h3 ~ span[style=white-space:pre]").text();
		}

		setVariableValue(name, value);
	}

}
