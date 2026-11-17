<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<!DOCTYPE html>
<html>
	<head>
		<title>LegacySpringApp</title>
	</head>
	
	<body>

		<h1>LegacySpringApp</h1><h2>Confidential admin report</h2>

		<%@ include file="_identity.jspf" %><p>Authorized administrators only.</p>

	</body>
</html>
