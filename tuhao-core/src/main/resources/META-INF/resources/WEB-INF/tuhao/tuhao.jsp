<%@ page contentType="text/html" pageEncoding="UTF-8" trimDirectiveWhitespaces="true"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ taglib prefix="t"   uri="http://net.aequologica.neo/jsp/jstl/layout"%>
<t:layout module="tuhao" >

  <h2>user</h2>
  <ul class="list-unstyled">
    <li>${fn:replace(model.user.name, '"', '')}</li>
    <li>${fn:replace(model.user.email, '"', '')}</li>
  </ul>

  <h2>repositories</h2>
  <ul class="list-unstyled">
    <c:forEach var="repo" items="${model.repos}">
      <li>${fn:replace(repo.full_name, '"', '')}</li>
    </c:forEach>
  </ul>
  
<!-- 
  <h2>organizations</h2>
  <ul class="list-unstyled">
    <c:forEach var="org" items="${model.orgs}">
      <li>${org.login}</li>
    </c:forEach>
  </ul>
 -->

</t:layout>

