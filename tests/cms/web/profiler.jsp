<%@ page language="java" import="org.w3c.dom.*, cms.www.*, cms.www.xml.*, edu.cornell.csuglab.cms.author.*" %>
<% Document displayData = (Document)session.getAttribute(AccessController.A_DISPLAYDATA);
Principal p = (Principal)session.getAttribute(AccessController.A_PRINCIPAL);
Element root = (Element)displayData.getElementsByTagName(XMLBuilder.TAG_ROOT).item(0);
NodeList actions = root.getElementsByTagName(XMLBuilder.TAG_REQUESTPROFILE);
%>

<script language="javascript" src="header.js"></script>
<style type="text/css">
	.IN {
		background-color: #DDDDDD;
	}
	
	.OUT {
		background-color: #FFFFFF;
	}
</style>
<h2>Recent Servlet Requests</h2>
<br>
<% for(int j=0; j < actions.getLength(); j++) {
	Element action = (Element) actions.item(j);
	NodeList calls = action.getChildNodes(); %>
	<table cellpadding="0" cellspacing="1" border="1" width="90%">
		<tr>
			<th>Method Name</th><th>IN/OUT</th><th>Context</th>
			<th>Total Time</th><th>Portion of Total Response Time</th><th>Time</th>
		</tr>
		<% for(int i=0; i < calls.getLength(); i++) { 
			Element method = (Element) calls.item(i); 
			String type = method.getAttribute(XMLBuilder.A_TYPE);
			boolean IN = type != null && type.equals("IN"); %>
			<tr class="<%= type %>">
				<td>&nbsp;<%= method.getAttribute(XMLBuilder.A_METHODNAME)%></td>
				<td>&nbsp;<%= type %></td>
				<td>&nbsp;<%= method.getAttribute(XMLBuilder.A_CONTEXT)%></td>
				<td>&nbsp;<%= method.getAttribute(XMLBuilder.A_TIMEPERIOD)%></td>
				<td>&nbsp;<%= method.getAttribute(XMLBuilder.A_PERCENT)%></td>
				<td>&nbsp;<%= method.getAttribute(XMLBuilder.A_TIME)%></td>
			</tr>
		<% } %>
	</table><br><br>
<% } %>

<jsp:include page="print-xml-tree.jsp" />
