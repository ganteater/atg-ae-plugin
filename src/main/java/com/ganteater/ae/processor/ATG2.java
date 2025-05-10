package com.ganteater.ae.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.ganteater.ae.AELogRecord;
import com.ganteater.ae.processor.annotation.CommandExamples;
import com.ganteater.ae.util.TestCase;
import com.ganteater.ae.util.xml.easyparser.EasyUtils;
import com.ganteater.ae.util.xml.easyparser.Node;

public class ATG2 extends BaseProcessor {

	private static final int DEFAULT_TIMEOUT = 5000;
	private static final String FILE_PREFIX = " File ";
	private static final String DYN_ADMIN_ROOT = "/dyn/admin/nucleus/";
	private static final String DEFAULT_PROTOCOL = "http://";

	@CommandExamples({
			"<SetProperty connection='type:property' component='type:string' property='type:string' value='type:string'/>" })
	public void runCommandSetProperty(Node action) throws IOException {
		String value = attr(action, "value");
		String property = attr(action, "property");

		if (value == null) {
			value = action.getTextNodes()[0].getText();
		}

		Map<String, String> conProps = (Map<String, String>) attrValue(action, "connection");
		String query = attr(action, "component");
		Connection connect = getConnect(conProps, query);

		value = replaceProperties(value);

		connect = connect.data("propertyName", property).data("newValue", value).data("change", "Change+Value");
		Document doc = auth(conProps, connect).post();
		debug(new AELogRecord(doc.html(), "html", "responce"));
		String result = doc.select("h3 ~ span").text();
		TestCase.assertEquals("Could not set property: " + property, value, result);
	}

	@CommandExamples({
			"<RQL name='type:property' connection='type:property' repository='type:string' item-descriptor='type:string' query='type:string' id-only='enum:false|true'/>" })
	public void runCommandRQL(Node action) throws IOException {
		String name = attr(action, "name");
		String itemDescriptor = attr(action, "item-descriptor");
		String query = attr(action, "query");
		if (query == null) {
			query = action.getTextNodes()[0].getText();
		}
		boolean idOnly = "true".equalsIgnoreCase(attr(action, "id-only"));

		query = "<query-items item-descriptor=\"" + itemDescriptor + "\" id-only=\"" + idOnly + "\">" + query
				+ "</query-items>";

		String repository = attr(action, "repository");
		if (!StringUtils.endsWith(repository, "/")) {
			repository += "/";
		}

		@SuppressWarnings("unchecked")
		Map<String, String> conProps = (Map<String, String>) attrValue(action, "connection");
		Connection connect = getConnect(conProps, repository);

		String username = (String) conProps.get("username");
		String password = (String) conProps.get("password");
		String authString = username + ":" + password;
		String encodedString = new String(Base64.encodeBase64(authString.getBytes()));

		Document doc = connect.header("Authorization", "Basic " + encodedString).data("xmltext", query).post();
		String value = doc.select("h2 ~ pre code").text();
		value = StringUtils.substringAfter(value, "\n");

		List<String> result = null;
		if (StringUtils.isNotBlank(value)) {
			if (idOnly) {
				result = Arrays.asList(StringUtils.splitByWholeSeparator(value, ", "));
			} else {
				result = Arrays.asList(StringUtils.splitByWholeSeparator(value, "\n\n"));
			}
		}

		setVariableValue(name, result);
	}

	@CommandExamples("<ModifyItem name='type:property' connection='type:property' repository='type:string'/>")
	public void runCommandModifyItem(Node action) throws IOException {
		@SuppressWarnings("unchecked")
		Map<String, String> conProps = (Map<String, String>) attrValue(action, "connection");
		StringBuffer result = new StringBuffer();
		String name = attr(action, "name");
		String repository = attr(action, "repository");
		if (!StringUtils.endsWith(repository, "/")) {
			repository += "/";
		}

		EasyUtils.removeTagId(action);
		for (Node node : action) {
			String xmlText = node.getXMLText();
			String value = getResult(repository, xmlText, conProps);
			if (result.length() > 0) {
				result.append("\n");
			}
			result.append(value);
		}
		if (name != null) {
			setVariableValue(name, result);
		}
	}

	@CommandExamples("<PrintItem name='type:property' connection='type:property' repository='type:string' item-descriptor='type:string' id='type:string' />")
	public void runCommandPrintItem(Node action) throws IOException {
		@SuppressWarnings("unchecked")
		Map<String, String> conProps = (Map<String, String>) attrValue(action, "connection");
		String repository = attr(action, "repository");
		if (!StringUtils.endsWith(repository, "/")) {
			repository += "/";
		}

		String itemDescriptor = attr(action, "item-descriptor");
		String id = attr(action, "id");
		StringBuilder query = new StringBuilder("<print-item item-descriptor=\"" + itemDescriptor + "\"");
		query.append(" id=\"" + id + "\"");
		query.append("/>");

		String value = getResult(repository, query.toString(), conProps);
		if (StringUtils.contains(value, "Can't find item with id=" + id + " for item-descriptor=" + itemDescriptor)) {
			throw new IllegalArgumentException(value);
		}
		String name = attr(action, "name");
		setVariableValue(name, StringUtils.substringAfter(value, "\n"));
	}

	@CommandExamples("<DeleteItem name='type:property' connection='type:property' repository='type:string' item-descriptor='type:string' id='type:string' />")
	public void runCommandDeleteItem(Node action) throws IOException {
		String repository = attr(action, "repository");
		if (!StringUtils.endsWith(repository, "/")) {
			repository += "/";
		}

		@SuppressWarnings("unchecked")
		Map<String, String> conProps = (Map<String, String>) attrValue(action, "connection");

		String itemDescriptor = attr(action, "item-descriptor");
		String id = attr(action, "id");
		String query = "<remove-item item-descriptor=\"" + itemDescriptor + "\" id=\"" + id + "\"/>";
		String value = getResult(repository, query, conProps);

		String name = attr(action, "name");
		setVariableValue(name, StringUtils.substringAfter(value, ":"));
	}

	@CommandExamples("<GetDescriptors name='type:property' connection='type:property' repository='type:string' body='type:property' />")
	public void runCommandGetDescriptors(Node action) throws IOException {
		String name = attr(action, "name");

		String repository = attr(action, "repository");
		if (!StringUtils.endsWith(repository, "/")) {
			repository += "/";
		}

		@SuppressWarnings("unchecked")
		Map<String, String> conProps = (Map<String, String>) attrValue(action, "connection");
		Connection connect = getConnect(conProps, repository);
		Document doc = connect.get();
		Elements thElements = doc.select("table:first-of-type th:eq(0)");
		List<String> repoItems = thElements.stream().skip(1).map(e -> e.text()).collect(Collectors.toList());

		setVariableValue(name, repoItems);
	}

	private String getResult(String repository, String xmltext, Map<String, String> conProps) throws IOException {
		Connection connect = getConnect(conProps, repository);

		xmltext = replaceProperties(xmltext);
		debug(new AELogRecord(xmltext, "xml", "xmltext"));
		connect = connect.data("xmltext", xmltext);

		Document doc = auth(conProps, connect).post();
		debug(new AELogRecord(doc.html(), "html", "responce"));

		String value = doc.select("h2 ~ h2 ~ pre code").text();

		if (StringUtils.isBlank(value)) {
			String text = doc.text();
			String searchStr = "Nucleus Service " + repository + " not found";
			if (StringUtils.contains(text, searchStr)) {
				throw new RuntimeException(searchStr);
			}
			String errorMsg = doc.select("pre code").text();
			if (StringUtils.isNotBlank(errorMsg)) {
				throw new RuntimeException(errorMsg);
			}
		}

		return value;
	}

	private Connection auth(Map<String, String> conProps, Connection connect) throws IOException {
		String username = (String) conProps.get("username");
		String password = (String) conProps.get("password");
		String authString = username + ":" + password;
		String encodedString = new String(Base64.encodeBase64(authString.getBytes()));

		return connect.header("Authorization", "Basic " + encodedString);
	}

	private Connection getConnect(Map<String, String> conProps, String query) {
		
		String protocol = (String) conProps.get("protocol");
		String host = (String) conProps.get("host");
		String port = (String) conProps.get("port");
		int timeout = Integer
				.parseInt(StringUtils.defaultIfBlank(conProps.get("timeout"), Integer.toString(DEFAULT_TIMEOUT)));

		String url = StringUtils.defaultIfBlank(protocol, DEFAULT_PROTOCOL) + host + (port != null ? ":" + port : "")
				+ DYN_ADMIN_ROOT + StringUtils.defaultIfEmpty(query, "");

		debug(new AELogRecord(url, "url", "dynadminUrl"));
		Connection connect = Jsoup.connect(url);
		connect.timeout(timeout);

		return connect;
	}

	@CommandExamples({ "<PropertyParser name='type:property' property='type:string' source='type:property' />" })
	public void runCommandPropertyParser(Node action) {
		String name = attr(action, "name");
		String property = attr(action, "property");
		Object source = attrValue(action, "source");

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
			"<GetProperty name='type:property'  component='' property='type:string' connection='type:property'/>" })
	public void runCommandGetProperty(Node action) throws IOException {
		@SuppressWarnings("unchecked")
		Map<String, String> conProps = (Map<String, String>) attrValue(action, "connection");

		String name = attr(action, "name");
		String property = attr(action, "property");
		if (property == null) {
			property = name;
		}

		String component = attr(action, "component");
		String query = component + "?propertyName=" + property;

		Connection connect = getConnect(conProps, query);

		Document doc = auth(conProps, connect).get();
		debug(new AELogRecord(doc.toString(), "html", name));
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

		Object result = value;

		if (value == null) {
			Elements select = doc.select("body > table:nth-child(15) > tbody > tr > td:nth-child(2)");
			if (select != null && !select.isEmpty()) {
				List<String> values = new ArrayList<>();
				for (Element element : select) {
					values.add(element.text());
				}
				result = values;
			}
		}

		value = doc.select("body > h1:nth-child(1)").text();
		if (StringUtils.contains(value, "Nucleus Service " + component + " not found")) {
			throw new IllegalArgumentException(value);
		}

		if (result == null) {
			result = doc.select("h3 ~ span[style=white-space:pre]").text();
		}

		setVariableValue(name, result);
	}

}
