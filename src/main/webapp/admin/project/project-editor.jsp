<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html>
<head>
<%@ include file="/_include/Head.jsp"%>
<title>项目管理</title>
</head>
<body>
<div class="rb-wrapper rb-fixed-sidebar rb-collapsible-sidebar rb-collapsible-sidebar-hide-logo rb-color-header">
	<jsp:include page="/_include/NavTop.jsp">
		<jsp:param value="项目管理" name="pageTitle"/>
	</jsp:include>
	<jsp:include page="/_include/NavLeftAdmin.jsp">
		<jsp:param value="projects" name="activeNav"/>
	</jsp:include>
	<div class="rb-content">
		<div class="page-head">
			<div class="float-left"><div class="page-head-title">项目管理</div></div>
			<div class="float-right pt-1">
				<button class=" btn btn-primary J_add" type="button"><i class="icon zmdi zmdi-plus"></i> 添加</button>
			</div>
			<div class="clearfix"></div>
		</div>
		<div class="main-content container-fluid pt-0" id="list">
			<div class="card ph">
				<div class="card-body">
					<div class="ph-item">
						<div class="ph-col-12">
							<div class="ph-row">
								<div class="ph-col-8"></div>
								<div class="ph-col-4 empty"></div>
								<div class="ph-col-12"></div>
							</div>
						</div>
					</div>
				</div>
			</div>
		</div>
	</div>
</div>
<%@ include file="/_include/Foot.jsp"%>
<script src="${baseUrl}/assets/js/project/project-editor.jsx" type="text/babel"></script>
</body>
</html>
