
    <footer class="bg-dark py-4 row d-print-none">
        <div class="container-fluid mx-sm-5">
            <div class="row align-items-center">
                <div class="col-6 col-sm-4 text-xs-center order-sm-2">
                    <ul class="list-inline mb-0">
                        <% if (config.site_footerMail) { %>
                        <li class="list-inline-item mx-2 h4" title="Email">
                            <a class="text-white" href="mailto:${config.site_footerMail}" rel="noopener noreferrer"
                               target="_blank">
                                <i class="fa fa-envelope"></i>
                            </a>
                        </li>
                        <% } %>
                        <% if (config.site_footerTwitter) { %>
                        <li class="list-inline-item mx-2 h4" title="Twitter">
                            <a class="text-white" href="${config.site_footerTwitter}" rel="noopener noreferrer"
                               target="_blank">
                                <i class="fab fa-twitter"></i>
                            </a>
                        </li>
                        <% } %>
                        <% if (config.site_footerSO) { %>
                        <li class="list-inline-item mx-2 h4" title="Stack Overflow">
                            <a class="text-white" href="${config.site_footerSO}" rel="noopener noreferrer"
                               target="_blank">
                                <i class="fab fa-stack-overflow"></i>
                            </a>
                        </li>
                        <% } %>
                    </ul>
                </div>
                <div class="col-6 col-sm-4 text-right text-xs-center order-sm-3">
                    <ul class="list-inline mb-0">
                        <% if (config.site_footerGithub) { %>
                        <li class="list-inline-item mx-2 h4" title="GitHub">
                            <a class="text-white" href="${config.site_footerGithub}" rel="noopener noreferrer"
                               target="_blank">
                                <i class="fab fa-github"></i>
                            </a>
                        </li>
                        <% } %>
                        <% if (config.site_footerSlack) { %>
                        <li class="list-inline-item mx-2 h4" title="Slack">
                            <a class="text-white" href="${config.site_footerSlack}" rel="noopener noreferrer"
                               target="_blank">
                                <i class="fab fa-slack"></i>
                            </a>
                        </li>
                        <% } %>
                    </ul>
                </div>
                <div class="col-12 col-sm-4 text-center py-2 order-sm-2">
                    <span class="text-muted">${config.site_footerText}</span>
                </div>
            </div>
        </div>
    </footer>

    <script src="${content.rootpath}js/popper.min.js"></script>
    <script src="${content.rootpath}js/bootstrap.min.js"></script>
    <script src="${content.rootpath}js/main.min.b5fc1b29d2465835844254bd4d804738eaf24c0cec53009b4c6899989be55a47.js"></script>
    <script src="${content.rootpath}js/blocks.js" ></script>
    <script src="${content.rootpath}js/prettify.js"></script>
    <script src="${content.rootpath}js/copy-n-paste.js"></script>
    <script src="${content.rootpath}js/submenucollapse.js"></script>
