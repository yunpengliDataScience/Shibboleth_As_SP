<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<!DOCTYPE html>

<html>
	<head>
		<title>LegacySpringApp</title>
	</head>
	<body>
	
		<h1>LegacySpringApp</h1><h2>Home</h2>
		<%@ include file="_identity.jspf" %>
		
		<p><a href="<c:url value="/user"/>">User page</a></p>
		
		<sec:authorize access="hasRole('ADMIN')">
			<p><a href="<c:url value="/admin"/>">Admin dashboard</a></p>
			<p><a href="<c:url value="/admin/report"/>">Admin report</a></p>
		</sec:authorize>
		
		<sec:authorize access="!hasRole('ADMIN')">
			<p>Admin links are hidden for regular users.</p>
		</sec:authorize>
	
	</body>
</html>
